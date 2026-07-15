package io.madrona.njord.ingest

import File
import OgrGeometry
import SqliteDb
import io.madrona.njord.ChartsConfig
import io.madrona.njord.RegionExportConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.RegionChart
import io.madrona.njord.db.RegionDao
import io.madrona.njord.geo.TileEncoder
import io.madrona.njord.geojson.BoundingBox
import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.GeoJsonObject
import io.madrona.njord.model.RegionManifestEntry
import io.madrona.njord.util.DistributedLock
import io.madrona.njord.util.gzipCompress
import io.madrona.njord.util.logger
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.time.Clock
import kotlin.time.Instant

private data class TileCoord(val z: Int, val x: Int, val y: Int)

class RegionExporter(
    private val config: ChartsConfig = Singletons.config,
    private val regionDir: File = Singletons.regionDir,
    private val regionDao: RegionDao = RegionDao(),
    private val distributedLock: DistributedLock = Singletons.distributedLock,
) {
    private val log = logger()
    private val jsonParser = Json

    sealed class ExportResult {
        object Rendered : ExportResult()
        object NothingToDo : ExportResult()
        object LockBusy : ExportResult()
    }

    /**
     * Renders at most one region — whichever configured region is currently stale — then
     * returns. Acquires [distributedLock] (shared with chart ingestion) for the duration of the
     * render, so this never runs concurrently with an ingest or another export anywhere in the
     * cluster.
     */
    suspend fun exportNext(): ExportResult {
        if (config.regionExports.isEmpty()) {
            log.info("no region exports configured, skipping")
            return ExportResult.NothingToDo
        }
        regionDir.mkdirs()
        val next = config.regionExports.firstOrNull { needsRebuild(it) } ?: return ExportResult.NothingToDo
        if (!distributedLock.tryAcquireLock()) return ExportResult.LockBusy
        try {
            runCatching { exportRegion(next) }
                .onFailure { log.error("region export failed for ${next.name}: ${it.message}") }
        } finally {
            distributedLock.tryClearLock()
        }
        return ExportResult.Rendered
    }

    /**
     * Deletes all rendered region archives.
     */
    fun clear() {
        if (regionDir.isDirectory()) {
            regionDir.listFiles(false).forEach { it.deleteRecursively() }
            log.info("region archives cleared")
        }
    }

    /**
     * Export a single region on-demand, always generating a new archive regardless of whether
     * one already exists. Retries acquiring [distributedLock] a few times so a transient hold by
     * the background export loop (or an in-progress ingest) doesn't silently drop this request.
     */
    suspend fun exportForced(regionConfig: RegionExportConfig) {
        regionDir.mkdirs()
        repeat(FORCED_LOCK_ATTEMPTS) { attempt ->
            if (distributedLock.tryAcquireLock()) {
                try {
                    runCatching { exportRegion(regionConfig, force = true) }
                        .onFailure { log.error("forced region export failed for ${regionConfig.name}: ${it.message}") }
                } finally {
                    distributedLock.tryClearLock()
                }
                return
            }
            if (attempt < FORCED_LOCK_ATTEMPTS - 1) delay(FORCED_LOCK_RETRY_DELAY_MS)
        }
        log.warn("forced region export for ${regionConfig.name} skipped — lock busy")
    }

    private suspend fun exportRegion(regionConfig: RegionExportConfig, force: Boolean = false) {
        log.info("exporting region ${regionConfig.name} force=$force")

        if (!force && !needsRebuild(regionConfig)) {
            log.info("region ${regionConfig.name} is up-to-date, skipping")
            return
        }

        val isWorld = regionConfig.name == WORLD_REGION_NAME
        val charts = if (isWorld) {
            emptyList()
        } else {
            val found = regionDao.findChartsInRegion(regionConfig.coverage) ?: run {
                log.warn("no charts found for region ${regionConfig.name}")
                return
            }
            if (found.isEmpty()) {
                log.info("no charts intersect region ${regionConfig.name}, skipping")
                return
            }
            found
        }

        val archiveName = "${regionConfig.name}_${currentTimestamp()}.mbtiles"
        val archiveFile = File(regionDir, archiveName)
        val tmpFile = File(regionDir, "$archiveName.tmp")

        if (isWorld) {
            log.info("writing world base map to ${archiveFile.getAbsolutePath()}")
        } else {
            log.info("writing ${charts.size} chart(s) to ${archiveFile.getAbsolutePath()}")
        }
        writeMbtilesArchive(tmpFile.getAbsolutePath().toString(), regionConfig, charts, isWorld)

        // Atomic rename
        if (tmpFile.renameTo(archiveFile.getAbsolutePath().toString()) == null) {
            log.error("failed to rename temp archive to ${archiveFile.getAbsolutePath()}")
            tmpFile.deleteRecursively()
            return
        }

        log.info("region ${regionConfig.name} archive created: $archiveName")
        regionDao.markRegionExported(regionConfig.name)
        pruneOldArchives(regionConfig.name)
    }

    private suspend fun writeMbtilesArchive(
        path: String,
        regionConfig: RegionExportConfig,
        charts: List<RegionChart>,
        isWorld: Boolean,
    ) {
        val tileCoords = if (isWorld) worldTileCoordinates() else compileTileCoordinates(charts)
        log.info("region ${regionConfig.name}: ${tileCoords.size} candidate tile(s)")

        SqliteDb.open(path).use { db ->
            db.exec(CREATE_METADATA_TABLE)
            db.exec(CREATE_TILES_TABLE)
            db.exec(CREATE_TILES_INDEX)

            val (written, minZ, maxZ) = renderAndWriteTiles(db, tileCoords, isWorld)
            writeMetadata(db, regionConfig, minZ, maxZ, isWorld)
            log.info("region ${regionConfig.name}: wrote $written/${tileCoords.size} non-empty tile(s)")
        }
    }

    /**
     * The full quad tree of tiles for zoom levels 0..[WORLD_MAX_ZOOM], covering the entire
     * earth. Used for the "WORLD" base map region, which has no charts to derive a sparse
     * tile set from.
     */
    private fun worldTileCoordinates(): Set<TileCoord> {
        val coords = mutableSetOf<TileCoord>()
        for (z in 0..WORLD_MAX_ZOOM) {
            val n = 1 shl z
            for (x in 0 until n) {
                for (y in 0 until n) {
                    coords.add(TileCoord(z, x, y))
                }
            }
        }
        return coords
    }

    /**
     * Compiles the sparse set of (z,x,y) tiles worth rendering: for every feature in every
     * chart intersecting the region, its own [MINZ, MAXZ] zoom-visibility range (from S-57
     * SCAMIN/SCAMAX) is capped at that feature's chart's own compiled-scale zoom (chart.zoom,
     * already derived at ingest time from DSPM_CSCL) — so an overview chart naturally yields a
     * shallow pyramid and a detailed chart a deeper one, with no separate config needed.
     */
    private suspend fun compileTileCoordinates(charts: List<RegionChart>): Set<TileCoord> {
        val tileSystem = Singletons.tileSystem
        val coords = mutableSetOf<TileCoord>()
        charts.forEach { chart ->
            val features = regionDao.findFeaturesForChart(chart.id) ?: return@forEach
            features.forEach { feature ->
                val props = jsonParser.parseToJsonElement(feature.propsJson).jsonObject
                val featMinZ = (props["MINZ"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
                val featMaxZ = (props["MAXZ"]?.jsonPrimitive?.intOrNull ?: 32).coerceAtMost(chart.zoom)
                if (featMinZ > featMaxZ) return@forEach

                val bbox = OgrGeometry.fromWkb4326(feature.geomWkb)?.envelope() ?: return@forEach
                for (z in featMinZ..featMaxZ) {
                    val maxTile = (1 shl z) - 1
                    val tl = tileSystem.latLngToTileXy(bbox.west, bbox.north, z)
                    val br = tileSystem.latLngToTileXy(bbox.east, bbox.south, z)
                    val xStart = tl.x.toInt().coerceIn(0, maxTile)
                    val xEnd = br.x.toInt().coerceIn(0, maxTile)
                    val yStart = tl.y.toInt().coerceIn(0, maxTile)
                    val yEnd = br.y.toInt().coerceIn(0, maxTile)
                    for (x in xStart..xEnd) {
                        for (y in yStart..yEnd) {
                            coords.add(TileCoord(z, x, y))
                        }
                    }
                }
            }
        }
        return coords
    }

    /**
     * Renders each candidate tile via the same [TileEncoder] used for live tile serving, skips
     * tiles with no real content, and gzip-compresses + inserts the rest. Tiles are rendered in
     * batches (suspend, outside any transaction) then written in a single sync db.transaction
     * per batch, since SqliteDb.transaction {} does not accept a suspend lambda.
     */
    private suspend fun renderAndWriteTiles(
        db: SqliteDb,
        tileCoords: Set<TileCoord>,
        isWorld: Boolean,
    ): Triple<Int, Int, Int> {
        var written = 0
        var minZ = Int.MAX_VALUE
        var maxZ = Int.MIN_VALUE
        db.prepare(INSERT_TILE).use { stmt ->
            tileCoords.sortedWith(compareBy({ it.z }, { it.x }, { it.y }))
                .chunked(TILE_INSERT_BATCH_SIZE)
                .forEach { batch ->
                    val rendered = batch.mapNotNull { coord ->
                        val encoder = TileEncoder(coord.x, coord.y, coord.z)
                        if (isWorld) encoder.addBaseMapOnly() else encoder.addCharts(false)
                        if (encoder.hasContent()) coord to gzipCompress(encoder.encode()) else null
                    }
                    if (rendered.isNotEmpty()) {
                        db.transaction {
                            rendered.forEach { (coord, bytes) ->
                                val tmsRow = xyzToTmsRow(coord.z, coord.y)
                                stmt.reset()
                                    .bindInt(1, coord.z)
                                    .bindInt(2, coord.x)
                                    .bindInt(3, tmsRow)
                                    .bindBlob(4, bytes)
                                    .step()
                                written++
                                minZ = minOf(minZ, coord.z)
                                maxZ = maxOf(maxZ, coord.z)
                            }
                        }
                    }
                }
        }
        return Triple(written, if (written > 0) minZ else 0, if (written > 0) maxZ else 0)
    }

    private fun writeMetadata(
        db: SqliteDb,
        regionConfig: RegionExportConfig,
        minZ: Int,
        maxZ: Int,
        isWorld: Boolean,
    ) {
        val env = if (isWorld) {
            WORLD_ENVELOPE
        } else {
            OgrGeometry.fromWkt4326(regionConfig.coverage)?.envelope()
        }
        val rows = buildList {
            add("name" to regionConfig.name)
            add("description" to regionConfig.description)
            add("format" to "pbf")
            add("type" to "baselayer")
            add("version" to "1")
            add("minzoom" to minZ.toString())
            add("maxzoom" to maxZ.toString())
            env?.let {
                add("bounds" to "${it.west},${it.south},${it.east},${it.north}")
                add("center" to "${(it.west + it.east) / 2},${(it.south + it.north) / 2},${(minZ + maxZ) / 2}")
            }
        }
        db.prepare(INSERT_METADATA).use { stmt ->
            db.transaction {
                rows.forEach { (name, value) ->
                    stmt.reset().bindText(1, name).bindText(2, value).step()
                }
            }
        }
    }

    private suspend fun needsRebuild(regionConfig: RegionExportConfig): Boolean =
        regionDao.regionNeedsRebuild(regionConfig.coverage, regionConfig.name) ?: true // fail-open: rebuild on DB error

    private fun pruneOldArchives(regionName: String) {
        val archives = archivesForRegion(regionName)
        if (archives.size > MAX_ARCHIVES) {
            archives.drop(MAX_ARCHIVES).forEach { old ->
                log.info("pruning old archive ${old.name}")
                old.deleteRecursively()
            }
        }
    }

    private fun archivesForRegion(regionName: String): List<File> {
        return regionDir.listFiles(false)
            .filter { it.name.startsWith(regionName) && it.name.endsWith(".mbtiles") }
            .sortedByDescending { parseArchiveTimestamp(regionName, it.name) ?: Instant.DISTANT_PAST }
    }

    private fun String.wktToGeojson() : GeoJsonObject {
        return OgrGeometry.fromWkt4326(this)?.geoJson() ?: Feature(geometry = null)
    }

    /**
     * Inverts [currentTimestamp]'s "yyyy-MM-ddTHH-mm-ss" format (dashes in place of colons,
     * for filesystem-safety) back into an [Instant], from an archive filename of the form
     * "${regionName}_${timestamp}.mbtiles".
     */
    private fun parseArchiveTimestamp(regionName: String, fileName: String): Instant? {
        val ts = fileName.removePrefix("${regionName}_").removeSuffix(".mbtiles")
        return runCatching {
            val colonized = ts.replaceFirst(Regex("T(\\d{2})-(\\d{2})-(\\d{2})$"), "T$1:$2:$3")
            LocalDateTime.parse(colonized).toInstant(TimeZone.currentSystemDefault())
        }.getOrNull()
    }

    /**
     * Builds a manifest entry for every region in [ChartsConfig.regionExports], regardless of
     * whether it has been rendered yet — [RegionManifestEntry.archive] and [RegionManifestEntry.createdAt]
     * are null until the first successful export.
     */
    fun buildManifest(): List<RegionManifestEntry> = config.regionExports.map { regionConfig ->
        val latestArchive = archivesForRegion(regionConfig.name).firstOrNull()
        val createdAt = latestArchive?.let { parseArchiveTimestamp(regionConfig.name, it.name) }
        RegionManifestEntry(
            name = regionConfig.name,
            description = regionConfig.description,
            coverage = regionConfig.coverage,
            coverageGeo = regionConfig.coverage.wktToGeojson(),
            archive = latestArchive?.name,
            createdAt = createdAt,
        )
    }

    private fun currentTimestamp(): String {
        // Use a filesystem-safe ISO-8601-style timestamp (colons replaced with hyphens)
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun Int.pad2() = toString().padStart(2, '0')
        return "${now.year}-${now.month.number.pad2()}-${now.day.pad2()}T${now.hour.pad2()}-${now.minute.pad2()}-${now.second.pad2()}"
    }

    companion object {
        const val MAX_ARCHIVES = 2
        const val WORLD_REGION_NAME = "WORLD"
        private const val FORCED_LOCK_ATTEMPTS = 3
        private const val FORCED_LOCK_RETRY_DELAY_MS = 5_000L
        private const val WORLD_MAX_ZOOM = 6
        private const val TILE_INSERT_BATCH_SIZE = 200
        private val WORLD_ENVELOPE = BoundingBox(-180.0, -90.0, 180.0, 90.0)

        /**
         * MBTiles uses TMS row order (Y=0 at bottom); everywhere else in this pipeline
         * (TileSystem, TileEncoder, the live /v1/tile route) uses XYZ (Y=0 at top) — this is
         * the one place that flips.
         */
        fun xyzToTmsRow(z: Int, y: Int): Int = ((1 shl z) - 1) - y

        private val CREATE_METADATA_TABLE = """
            CREATE TABLE IF NOT EXISTS metadata (
                name  TEXT NOT NULL,
                value TEXT NOT NULL
            );
        """.trimIndent()

        private val CREATE_TILES_TABLE = """
            CREATE TABLE IF NOT EXISTS tiles (
                zoom_level  INTEGER NOT NULL,
                tile_column INTEGER NOT NULL,
                tile_row    INTEGER NOT NULL,
                tile_data   BLOB    NOT NULL
            );
        """.trimIndent()

        private val CREATE_TILES_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS tile_index
                ON tiles (zoom_level, tile_column, tile_row);
        """.trimIndent()

        private const val INSERT_METADATA = """
            INSERT INTO metadata (name, value) VALUES (?, ?);
        """

        private const val INSERT_TILE = """
            INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data)
            VALUES (?, ?, ?, ?);
        """
    }
}
