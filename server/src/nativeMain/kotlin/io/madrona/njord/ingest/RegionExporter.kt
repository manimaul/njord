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
import io.madrona.njord.util.gzipCompress
import io.madrona.njord.util.logger
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.time.Clock

@Serializable
data class RegionManifestEntry(
    val name: String,
    val description: String,
    val coverage: String,
    val archive: String,
)

private data class TileCoord(val z: Int, val x: Int, val y: Int)

class RegionExporter(
    private val config: ChartsConfig = Singletons.config,
    private val regionDir: File = Singletons.regionDir,
    private val regionDao: RegionDao = RegionDao(),
) {
    private val log = logger()
    private val json = Json { prettyPrint = true }
    private val jsonParser = Json

    suspend fun exportAll() {
        if (config.regionExports.isEmpty()) {
            log.info("no region exports configured, skipping")
            return
        }
        regionDir.mkdirs()
        config.regionExports.forEach { regionConfig ->
            runCatching { exportRegion(regionConfig) }
                .onFailure { log.error("region export failed for ${regionConfig.name}: ${it.message}") }
        }
        updateManifest()
    }

    /**
     * Export a single region on-demand, always generating a new archive regardless of whether
     * one already exists. Updates the manifest after writing.
     */
    suspend fun exportForced(regionConfig: RegionExportConfig) {
        regionDir.mkdirs()
        runCatching { exportRegion(regionConfig, force = true) }
            .onFailure { log.error("forced region export failed for ${regionConfig.name}: ${it.message}") }
        updateManifest()
    }

    private suspend fun exportRegion(regionConfig: RegionExportConfig, force: Boolean = false) {
        log.info("exporting region ${regionConfig.name} force=$force")

        val charts = regionDao.findChartsInRegion(regionConfig.coverage) ?: run {
            log.warn("no charts found for region ${regionConfig.name}")
            return
        }
        if (charts.isEmpty()) {
            log.info("no charts intersect region ${regionConfig.name}, skipping")
            return
        }

        if (!force && !needsRebuild(regionConfig)) {
            log.info("region ${regionConfig.name} is up-to-date, skipping")
            return
        }

        val archiveName = "${regionConfig.name}_${currentTimestamp()}.mbtiles"
        val archiveFile = File(regionDir, archiveName)
        val tmpFile = File(regionDir, "$archiveName.tmp")

        log.info("writing ${charts.size} chart(s) to ${archiveFile.getAbsolutePath()}")
        writeMbtilesArchive(tmpFile.getAbsolutePath().toString(), regionConfig, charts)

        // Atomic rename
        if (tmpFile.renameTo(archiveFile.getAbsolutePath().toString()) == null) {
            log.error("failed to rename temp archive to ${archiveFile.getAbsolutePath()}")
            tmpFile.deleteRecursively()
            return
        }

        log.info("region ${regionConfig.name} archive created: $archiveName")
        pruneOldArchives(regionConfig.name)
    }

    private suspend fun writeMbtilesArchive(
        path: String,
        regionConfig: RegionExportConfig,
        charts: List<RegionChart>,
    ) {
        val tileCoords = compileTileCoordinates(charts)
        log.info("region ${regionConfig.name}: ${tileCoords.size} candidate tile(s)")

        SqliteDb.open(path).use { db ->
            db.exec(CREATE_METADATA_TABLE)
            db.exec(CREATE_TILES_TABLE)
            db.exec(CREATE_TILES_INDEX)

            val (written, minZ, maxZ) = renderAndWriteTiles(db, tileCoords)
            writeMetadata(db, regionConfig, minZ, maxZ)
            log.info("region ${regionConfig.name}: wrote $written/${tileCoords.size} non-empty tile(s)")
        }
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
    private suspend fun renderAndWriteTiles(db: SqliteDb, tileCoords: Set<TileCoord>): Triple<Int, Int, Int> {
        var written = 0
        var minZ = Int.MAX_VALUE
        var maxZ = Int.MIN_VALUE
        db.prepare(INSERT_TILE).use { stmt ->
            tileCoords.sortedWith(compareBy({ it.z }, { it.x }, { it.y }))
                .chunked(TILE_INSERT_BATCH_SIZE)
                .forEach { batch ->
                    val rendered = batch.mapNotNull { coord ->
                        val encoder = TileEncoder(coord.x, coord.y, coord.z)
                        encoder.addCharts(false)
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

    private fun writeMetadata(db: SqliteDb, regionConfig: RegionExportConfig, minZ: Int, maxZ: Int) {
        val env = OgrGeometry.fromWkt4326(regionConfig.coverage)?.envelope()
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

    private fun needsRebuild(regionConfig: RegionExportConfig): Boolean {
        val existing = archivesForRegion(regionConfig.name)
        if (existing.isEmpty()) return true
        // Always rebuild — the caller (RegionExportWorker) only runs when new charts were ingested
        return true
    }

    private fun pruneOldArchives(regionName: String) {
        val archives = archivesForRegion(regionName)
            .sortedByDescending { it.name }
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
    }

    private fun updateManifest() {
        val entries = config.regionExports.mapNotNull { regionConfig ->
            val latestArchive = archivesForRegion(regionConfig.name)
                .sortedByDescending { it.name }
                .firstOrNull() ?: return@mapNotNull null
            RegionManifestEntry(
                name = regionConfig.name,
                description = regionConfig.description,
                coverage = regionConfig.coverage,
                archive = latestArchive.name,
            )
        }
        val manifestFile = File(regionDir, MANIFEST_FILE)
        manifestFile.writeBytes(json.encodeToString(entries).encodeToByteArray())
        log.info("manifest updated with ${entries.size} region(s)")
    }

    private fun currentTimestamp(): String {
        // Use a filesystem-safe ISO-8601-style timestamp (colons replaced with hyphens)
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun Int.pad2() = toString().padStart(2, '0')
        return "${now.year}-${now.monthNumber.pad2()}-${now.dayOfMonth.pad2()}T${now.hour.pad2()}-${now.minute.pad2()}-${now.second.pad2()}"
    }

    companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val MAX_ARCHIVES = 2
        private const val TILE_INSERT_BATCH_SIZE = 200

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
