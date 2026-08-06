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
import io.madrona.njord.geojson.Point
import io.madrona.njord.geojson.jsonStr
import io.madrona.njord.model.RegionManifestEntry
import io.madrona.njord.util.DistributedLock
import io.madrona.njord.util.gzipCompress
import io.madrona.njord.util.logger
import io.madrona.njord.util.sha256File
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

    private suspend fun exportRegion(regionConfig: RegionExportConfig) {
        log.info("exporting region ${regionConfig.name}")

        if (!needsRebuild(regionConfig)) {
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

        val stamp = currentTimestamp()
        val archiveName = "${regionConfig.name}_$stamp.mbtiles"
        val archiveFile = File(regionDir, archiveName)
        val tmpFile = File(regionDir, "$archiveName.tmp")

        // `njord:generated_at` must be the same instant `buildManifest()` reports as
        // RegionManifestEntry.createdAt, and that one is derived by parsing the filename stem —
        // so derive this one the same way rather than sampling the clock a second time (see
        // docs/REGION_DATA_EXPORT_MOBILE.md §5.1).
        val generatedAt = parseTimestampStem(stamp) ?: run {
            log.error("could not parse archive timestamp stem '$stamp'")
            return
        }

        if (isWorld) {
            log.info("writing world base map to ${archiveFile.getAbsolutePath()}")
        } else {
            log.info("writing ${charts.size} chart(s) to ${archiveFile.getAbsolutePath()}")
        }
        writeMbtilesArchive(tmpFile.getAbsolutePath().toString(), regionConfig, charts, isWorld, generatedAt)

        // Atomic rename
        if (tmpFile.renameTo(archiveFile.getAbsolutePath().toString()) == null) {
            log.error("failed to rename temp archive to ${archiveFile.getAbsolutePath()}")
            tmpFile.deleteRecursively()
            return
        }

        log.info("region ${regionConfig.name} archive created: $archiveName")
        writeChecksumSidecar(archiveFile)
        regionDao.markRegionExported(regionConfig.name)
        pruneOldArchives(regionConfig.name)
    }

    /**
     * Writes the sparse tile archive *and* the chart catalog that rides inside it — an MBTiles
     * file is a SQLite database and MapLibre's MBTilesFileSource only ever reads `metadata` and
     * `tiles`, so the `charts`/`chart_tiles` catalog tables are invisible to it while keeping the
     * region to one downloadable file with one checksum (docs/REGION_DATA_EXPORT_MOBILE.md §5).
     */
    private suspend fun writeMbtilesArchive(
        path: String,
        regionConfig: RegionExportConfig,
        charts: List<RegionChart>,
        isWorld: Boolean,
        generatedAt: Instant,
    ) {
        SqliteDb.open(path).use { db ->
            db.exec(CREATE_METADATA_TABLE)
            db.exec(CREATE_TILES_TABLE)
            db.exec(CREATE_TILES_INDEX)
            db.exec(CREATE_CHARTS_TABLE)
            db.exec(CREATE_CHART_TILES_TABLE)

            // Charts first: chart_tiles.chart_name references them, and although SQLite leaves
            // foreign keys off by default, edges pointing at rows that don't exist yet would break
            // the moment anything opened this file with the pragma on.
            writeCharts(db, charts)

            // Edges stream straight to disk as each chart is compiled, so a dense region never
            // holds the per-chart breakdown in memory — only the merged candidate set, which the
            // render pass needs anyway.
            val tileCoords = if (isWorld) {
                worldTileCoordinates()
            } else {
                compileTileCoordinates(charts) { chartName, coords ->
                    writeChartTileEdges(db, chartName, coords)
                }
            }
            log.info("region ${regionConfig.name}: ${tileCoords.size} candidate tile(s)")

            val (written, minZ, maxZ) = renderAndWriteTiles(db, tileCoords, isWorld)

            // Candidate edges are a superset of the tiles actually written — the encoder finds no
            // content for many of them, which is what makes the archive sparse. Drop the dangling
            // ones before indexing, so the device's refcount doesn't believe in tiles that were
            // never installed (§5.2).
            db.exec(PRUNE_ORPHANED_CHART_TILES)
            db.exec(CREATE_CHART_TILES_INDEX)

            writeMetadata(db, regionConfig, minZ, maxZ, isWorld, generatedAt, charts.size, written)
            log.info("region ${regionConfig.name}: wrote $written/${tileCoords.size} non-empty tile(s)")
        }
    }

    /**
     * One `charts` row per chart in this region's archive, mirroring the server's `charts` table
     * with PostGIS/JSONB flattened to TEXT. The server's surrogate `id` is deliberately not
     * carried across: it is meaningless on a device that merges archives exported at different
     * times, so the cell name (DSID_DSNM) is the archive's chart identity (§5.1).
     */
    private fun writeCharts(db: SqliteDb, charts: List<RegionChart>) {
        if (charts.isEmpty()) return
        db.prepare(INSERT_CHART).use { stmt ->
            db.transaction {
                charts.forEach { chart ->
                    val covrGeoJson = OgrGeometry.fromWkb4326(chart.covrWkb)?.geoJson()?.jsonStr()
                        ?: run {
                            log.warn("chart ${chart.name}: could not convert covr WKB to GeoJSON")
                            "null"
                        }
                    stmt.reset()
                        .bindText(1, chart.name)
                        .bindInt(2, chart.scale)
                        .bindText(3, chart.fileName)
                        .bindText(4, chart.updated)
                        .bindText(5, chart.issued)
                        .bindInt(6, chart.zoom)
                        .bindText(7, covrGeoJson)
                        .bindText(8, chart.dsidPropsJson)
                        .bindText(9, chart.chartTxtJson)
                        .bindText(10, chart.ingestedAt)
                        .step()
                }
            }
        }
    }

    /**
     * The chart → tile edges for one chart, in the same column names and TMS row convention as
     * the `tiles` table alongside them, so the device's uninstall sweep can correlate the two by
     * bare column name (§5.1).
     */
    private fun writeChartTileEdges(db: SqliteDb, chartName: String, coords: Set<TileCoord>) {
        if (coords.isEmpty()) return
        db.prepare(INSERT_CHART_TILE).use { stmt ->
            db.transaction {
                coords.forEach { coord ->
                    stmt.reset()
                        .bindText(1, chartName)
                        .bindInt(2, coord.z)
                        .bindInt(3, coord.x)
                        .bindInt(4, xyzToTmsRow(coord.z, coord.y))
                        .step()
                }
            }
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
     *
     * Returns the merged candidate set for the render pass, and hands each chart's own candidate
     * set to [onChartCoords] as it is compiled so the caller can persist the per-chart breakdown
     * (which the merged set discards) without holding it all in memory.
     */
    private suspend fun compileTileCoordinates(
        charts: List<RegionChart>,
        onChartCoords: (chartName: String, coords: Set<TileCoord>) -> Unit,
    ): Set<TileCoord> {
        val tileSystem = Singletons.tileSystem
        val coords = mutableSetOf<TileCoord>()
        charts.forEach { chart ->
            val features = regionDao.findFeaturesForChart(chart.id) ?: return@forEach
            val chartCoords = mutableSetOf<TileCoord>()
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
                            chartCoords.add(TileCoord(z, x, y))
                        }
                    }
                }
            }
            onChartCoords(chart.name, chartCoords)
            coords.addAll(chartCoords)
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
        generatedAt: Instant,
        chartCount: Int,
        tileCount: Int,
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
            // Catalog bookkeeping. MapLibre turns every metadata row into a TileJSON member and
            // ignores the members it doesn't know, so these are inert to it as long as they don't
            // collide with the TileJSON names above — hence the "njord:" prefix (§5.1).
            add("njord:schema_version" to CATALOG_SCHEMA_VERSION.toString())
            add("njord:region" to regionConfig.name)
            add("njord:chart_count" to chartCount.toString())
            add("njord:tile_count" to tileCount.toString())
            add("njord:generated_at" to generatedAt.toString())
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
                sidecarFor(old).takeIf { it.exists() }?.deleteRecursively()
            }
        }
    }

    private fun sidecarFor(archive: File) = File(regionDir, "${archive.name}.sha256")

    /**
     * The archive's sha256 (lowercase hex), from its cached sidecar file — computed and written
     * on the first call for archives that predate checksum sidecars. Archives are immutable once
     * renamed into place, so a cached checksum never goes stale.
     */
    private fun checksumForArchive(archive: File): String? {
        if (sidecarFor(archive).isFile()) {
            sidecarFor(archive).readContents()
                .trim()
                .split(Regex("\\s+"))
                .firstOrNull()
                ?.takeIf { it.length == 64 }
                ?.let { return it }
        }
        return writeChecksumSidecar(archive)
    }

    private fun writeChecksumSidecar(archive: File): String? {
        val checksum = sha256File(archive) ?: run {
            log.error("failed to compute sha256 for ${archive.name}")
            return null
        }
        // sha256sum output format, so `sha256sum -c <name>.sha256` works from the region dir
        sidecarFor(archive).write("$checksum  ${archive.name}\n")
        return checksum
    }

    private fun archivesForRegion(regionName: String): List<File> {
        return regionDir.listFiles(false)
            .filter { it.name.startsWith(regionName) && it.name.endsWith(".mbtiles") }
            .sortedByDescending { parseArchiveTimestamp(regionName, it.name) ?: Instant.DISTANT_PAST }
    }

    private fun String.wktToGeojson() : GeoJsonObject {
        return OgrGeometry.fromWkt4326(this)?.geoJson() ?: Feature(geometry = null)
    }

    private fun String.wktToLabelPoint(): Point? {
        val centroid = OgrGeometry.fromWkt4326(this)?.centroidOfLargestPart() ?: return null
        val x = centroid.pointX ?: return null
        val y = centroid.pointY ?: return null
        return Point(x, y)
    }

    /**
     * Inverts [currentTimestamp]'s "yyyy-MM-ddTHH-mm-ss" format (dashes in place of colons,
     * for filesystem-safety) back into an [Instant], from an archive filename of the form
     * "${regionName}_${timestamp}.mbtiles".
     */
    private fun parseArchiveTimestamp(regionName: String, fileName: String): Instant? =
        parseTimestampStem(fileName.removePrefix("${regionName}_").removeSuffix(".mbtiles"))

    /**
     * Inverts a single [currentTimestamp] stem. [currentTimestamp] writes server-local time with
     * no offset marker, so this has to interpret it in the same zone — which is also why the
     * `njord:generated_at` metadata row is derived from the stem rather than re-sampled: two
     * samplings either side of a DST boundary would disagree by a whole offset (§5.1).
     */
    private fun parseTimestampStem(ts: String): Instant? = runCatching {
        val colonized = ts.replaceFirst(Regex("T(\\d{2})-(\\d{2})-(\\d{2})$"), "T$1:$2:$3")
        LocalDateTime.parse(colonized).toInstant(TimeZone.currentSystemDefault())
    }.getOrNull()

    /**
     * Builds a manifest entry for every region in [ChartsConfig.regionExports], regardless of
     * whether it has been rendered yet — [RegionManifestEntry.archive], [RegionManifestEntry.archiveSize],
     * [RegionManifestEntry.archiveSha256] and [RegionManifestEntry.createdAt] are null until the
     * first successful export.
     */
    fun buildManifest(): List<RegionManifestEntry> = config.regionExports.map { regionConfig ->
        val latestArchive = archivesForRegion(regionConfig.name).firstOrNull()
        val createdAt = latestArchive?.let { parseArchiveTimestamp(regionConfig.name, it.name) }
        RegionManifestEntry(
            name = regionConfig.name,
            description = regionConfig.description,
            coverage = regionConfig.coverage,
            coverageGeo = regionConfig.coverage.wktToGeojson(),
            labelPoint = regionConfig.coverage.wktToLabelPoint(),
            archive = latestArchive?.name,
            archiveSize = latestArchive?.size(),
            archiveSha256 = latestArchive?.let { checksumForArchive(it) },
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

        /** Bumped when the `charts`/`chart_tiles` shape changes, so a client can reject a file it doesn't understand. */
        const val CATALOG_SCHEMA_VERSION = 1

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

        internal val CREATE_TILES_TABLE = """
            CREATE TABLE IF NOT EXISTS tiles (
                zoom_level  INTEGER NOT NULL,
                tile_column INTEGER NOT NULL,
                tile_row    INTEGER NOT NULL,
                tile_data   BLOB    NOT NULL
            );
        """.trimIndent()

        internal val CREATE_TILES_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS tile_index
                ON tiles (zoom_level, tile_column, tile_row);
        """.trimIndent()

        private const val INSERT_METADATA = """
            INSERT INTO metadata (name, value) VALUES (?, ?);
        """

        internal const val INSERT_TILE = """
            INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data)
            VALUES (?, ?, ?, ?);
        """

        /**
         * Mirrors the server's `charts` table (see DbMigrations.kt) with PostGIS/JSONB types
         * flattened to TEXT. `name` (DSID_DSNM) is the primary key and the server's surrogate
         * `charts.id` is not carried across at all — see [writeCharts].
         */
        internal val CREATE_CHARTS_TABLE = """
            CREATE TABLE IF NOT EXISTS charts (
                name        TEXT    PRIMARY KEY,
                scale       INTEGER NOT NULL,
                file_name   TEXT    NOT NULL,
                updated     TEXT    NOT NULL,
                issued      TEXT    NOT NULL,
                zoom        INTEGER NOT NULL,
                covr        TEXT    NOT NULL,
                dsid_props  TEXT    NOT NULL,
                chart_txt   TEXT    NOT NULL,
                ingested_at TEXT    NOT NULL
            );
        """.trimIndent()

        internal val CREATE_CHART_TILES_TABLE = """
            CREATE TABLE IF NOT EXISTS chart_tiles (
                chart_name  TEXT    NOT NULL REFERENCES charts(name) ON DELETE CASCADE,
                zoom_level  INTEGER NOT NULL,
                tile_column INTEGER NOT NULL,
                tile_row    INTEGER NOT NULL,
                PRIMARY KEY (chart_name, zoom_level, tile_column, tile_row)
            ) WITHOUT ROWID;
        """.trimIndent()

        /** The reverse lookup ("which charts require this tile") the device's uninstall sweep needs. */
        internal val CREATE_CHART_TILES_INDEX = """
            CREATE INDEX IF NOT EXISTS chart_tiles_tile_idx
                ON chart_tiles (zoom_level, tile_column, tile_row);
        """.trimIndent()

        internal val PRUNE_ORPHANED_CHART_TILES = """
            DELETE FROM chart_tiles WHERE NOT EXISTS (
                SELECT 1 FROM tiles t
                WHERE t.zoom_level  = chart_tiles.zoom_level
                  AND t.tile_column = chart_tiles.tile_column
                  AND t.tile_row    = chart_tiles.tile_row);
        """.trimIndent()

        internal const val INSERT_CHART = """
            INSERT OR REPLACE INTO charts
                (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt, ingested_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """

        internal const val INSERT_CHART_TILE = """
            INSERT OR IGNORE INTO chart_tiles (chart_name, zoom_level, tile_column, tile_row)
            VALUES (?, ?, ?, ?);
        """
    }
}
