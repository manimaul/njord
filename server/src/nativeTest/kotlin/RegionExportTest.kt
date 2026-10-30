@file:OptIn(ExperimentalForeignApi::class)

import io.madrona.njord.RegionExportConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.RegionDao
import io.madrona.njord.ingest.RegionExporter
import io.madrona.njord.model.RegionManifestEntry
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.FixtureMode
import io.madrona.njord.testdata.fixtures
import io.madrona.njord.util.DistributedLock
import io.madrona.njord.util.sha256File
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import platform.posix.setenv
import platform.posix.tzset
import tile.decodeMvtToJson
import util.gunzip
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * P-RE-1 … P-RE-5: the region archive contract (docs/REGION_DATA_EXPORT_MOBILE.md), produced by
 * the real [RegionExporter] from the fixture cells. The archive is a SQLite file mobile clients
 * replay, so its DDL (`regions/schema.sql`), its `metadata` rows, the tile coordinate set and the
 * decoded tiles are all pinned; the decoded tiles must equal what `/v1/tile` serves for the same
 * coordinates (P-TE-5).
 *
 * Archive names and `njord:generated_at` are wall-clock stamps in server local time, so the tests
 * run under `TZ=America/Los_Angeles` (set here before kotlinx-datetime first reads the zone) and
 * pin the stem round trip including the DST transitions; the metadata fixture pins the instant's
 * format rather than its value.
 */
class RegionExportTest {

    private val regionName = "REGION_TEST"
    private val regionWkt = "POLYGON ((-124 46.5, -122 46.5, -122 47.5, -124 47.5, -124 46.5))"
    private val worldWkt = "POLYGON ((-180 -90,180 -90,180 90,-180 90,-180 -90))"

    private lateinit var regionDir: File
    private lateinit var regionDao: RegionDao
    private lateinit var lock: DistributedLock
    private lateinit var exporter: RegionExporter

    @BeforeTest
    fun before() {
        setenv("TZ", "America/Los_Angeles", 1)
        tzset()
        TestCharts.ensureSeeded()
        assertEquals("America/Los_Angeles", TimeZone.currentSystemDefault().id, "run the server tests with TZ=America/Los_Angeles; the archive stem fixtures are zone-dependent")
        regionDir = TestEnv.tempDir("regions")
        regionDao = RegionDao(TestEnv.ds)
        lock = DistributedLock(lockFile = File(TestEnv.tempDir("regions_lock"), "lock"))
        exporter = exporter(listOf(RegionExportConfig(regionName, "fixture cells", regionWkt), RegionExportConfig(RegionExporter.WORLD_REGION_NAME, "World Base Map", worldWkt)))
        runTest {
            regionDao.clearRegionExportState(regionName)
            regionDao.clearRegionExportState(RegionExporter.WORLD_REGION_NAME)
        }
    }

    private fun exporter(regions: List<RegionExportConfig>, dir: File = regionDir) = RegionExporter(
        config = Singletons.config.copy(regionExports = regions),
        regionDir = dir,
        regionDao = regionDao,
        distributedLock = lock,
    )

    private fun archives(name: String) = regionDir.listFiles(false).filter { it.name.startsWith("${name}_") && it.name.endsWith(".mbtiles") }

    private fun SqliteDb.rows(sql: String, read: (SqliteStatement) -> List<Any?>): List<List<Any?>> =
        prepare(sql).use { stmt -> buildList { while (stmt.step()) add(read(stmt)) } }

    /** A plain-text fixture with a leading `--` comment header, written in regenerate mode and compared in verify mode. */
    private fun sqlFixture(path: String, header: FixtureHeader, body: String) {
        val text = buildString {
            appendLine("-- pins: ${header.pins}")
            appendLine("-- decisions: ${header.decisions.joinToString(", ")}")
            appendLine("-- generator: ${header.generator}")
            header.source?.let { appendLine("-- source: $it") }
            appendLine()
            append(body)
        }
        when (fixtures.mode) {
            FixtureMode.REGENERATE -> fixtures.file(path).also { it.parentFile()?.mkdirs() }.write(text)
            FixtureMode.VERIFY -> assertEquals(fixtures.readText(path), text, "fixture $path differs; regenerate with NJORD_FIXTURES=regenerate and review the diff")
        }
    }

    @Serializable
    data class ArchiveSummary(
        val tiles: List<List<Int>>,
        val charts: List<List<String>>,
        val chartTileEdges: Map<String, Int>,
        val minZoom: Int,
        val maxZoom: Int,
    )

    @Test
    fun `P-RE-1 P-RE-2 P-RE-3 the exported archive - schema metadata tiles catalog and checksum`() = runTest {
        assertEquals(true, regionDao.regionNeedsRebuild(regionWkt, regionName))
        assertIs<RegionExporter.ExportResult.Rendered>(exporter.exportNext())
        val archive = archives(regionName).single()
        assertTrue(lock.lockFile.isEmpty(), "the shared lock is released after the render")
        assertEquals(false, regionDao.regionNeedsRebuild(regionWkt, regionName), "markRegionExported moved the watermark")

        // name and generated_at agree and are the same instant
        val stem = archive.name.removePrefix("${regionName}_").removeSuffix(".mbtiles")
        val generatedAt = assertNotNull(exporter.parseTimestampStem(stem))
        assertTrue(Clock.System.now() - generatedAt < kotlin.time.Duration.parse("2m"), "$stem is now-ish")

        // sidecar
        val sidecar = File(regionDir, "${archive.name}.sha256")
        assertEquals("${sha256File(archive)}  ${archive.name}\n", sidecar.readContents())

        SqliteDb.open(archive.getAbsolutePath().toString()).use { db ->
            val ddl = db.rows("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY type, name") { listOf(it.columnText(0)) }
                .joinToString(";\n\n") { it[0].toString() } + ";\n"
            sqlFixture(
                "regions/schema.sql",
                FixtureHeader(
                    pins = "sqlite_master DDL of a region archive: metadata, tiles (+ unique tile_index), charts, chart_tiles (WITHOUT ROWID, FK to charts) and chart_tiles_tile_idx",
                    decisions = listOf("RE-1"),
                    generator = "RegionExportTest",
                ),
                ddl,
            )

            val metadata = db.rows("SELECT name, value FROM metadata ORDER BY rowid") { listOf(it.columnText(0), it.columnText(1)) }
                .associate { it[0].toString() to it[1].toString() }
            assertEquals(generatedAt.toString(), metadata["njord:generated_at"], "generated_at is derived from the archive stem")
            assertEquals(archive.size() > 0, true)
            val worldArchive = exportWorld()
            val worldMetadata = SqliteDb.open(worldArchive.getAbsolutePath().toString()).use { w ->
                w.rows("SELECT name, value FROM metadata ORDER BY rowid") { listOf(it.columnText(0), it.columnText(1)) }.associate { it[0].toString() to it[1].toString() }
            }
            fixtures.table(
                "regions/metadata.json",
                FixtureHeader(
                    pins = "every metadata row of a region archive in insertion order; njord:generated_at is an RFC 3339 instant equal to the archive name stem and replaced by <instant> here; bounds/center come from the configured coverage (WORLD: -180..180 x -90..90), min/maxzoom from the tiles written",
                    decisions = listOf("RE-1", "RE-2"),
                    generator = "RegionExportTest",
                    source = goldenSource(),
                ),
                listOf(regionName, RegionExporter.WORLD_REGION_NAME),
            ) { name -> (if (name == regionName) metadata else worldMetadata).mapValues { (k, v) -> if (k == "njord:generated_at") "<instant>" else v } }

            // catalog tables
            assertEquals(emptyList(), db.rows("PRAGMA foreign_key_check") { listOf(it.columnText(0)) }, "no dangling chart_tiles edge")
            val tiles = db.rows("SELECT zoom_level, tile_column, tile_row FROM tiles ORDER BY 1, 2, 3") { listOf(it.columnInt(0), it.columnInt(1), it.columnInt(2)) }
                .map { it.map { v -> v as Int } }
            val charts = db.rows("SELECT name, scale, zoom, file_name, updated, issued, dsid_props FROM charts ORDER BY name") { s -> (0..6).map { s.columnText(it) ?: "" } }
                .map { it.map { v -> v.toString() } }
            val edges = db.rows("SELECT chart_name, count(*) FROM chart_tiles GROUP BY chart_name ORDER BY chart_name") { listOf(it.columnText(0), it.columnInt(1)) }
                .associate { it[0].toString() to it[1] as Int }
            val summary = ArchiveSummary(
                tiles = tiles,
                charts = charts,
                chartTileEdges = edges,
                minZoom = metadata["minzoom"]!!.toInt(),
                maxZoom = metadata["maxzoom"]!!.toInt(),
            )
            fixtures.table(
                "regions/archive.json",
                FixtureHeader(
                    pins = "the archive of the fixture cells: every (zoom_level, tile_column, tms tile_row) written (candidates from feature MINZ..min(MAXZ, chart.zoom) that the encoder found content for), the charts rows (ingested_at excluded), chart_tiles edges per chart after pruning",
                    decisions = listOf("RE-1", "RE-5", "TE-1", "TE-2"),
                    generator = "RegionExportTest",
                    source = goldenSource(),
                ),
                listOf(regionName),
            ) { summary }
            assertEquals(TestCharts.all.sorted(), charts.map { it[0] })
            assertTrue(tiles.isNotEmpty())
            edges.forEach { (chart, n) -> assertTrue(n > 0, chart) }
            val edgeCoords = db.rows("SELECT DISTINCT zoom_level, tile_column, tile_row FROM chart_tiles") { listOf(it.columnInt(0), it.columnInt(1), it.columnInt(2)) }
            assertTrue(tiles.containsAll(edgeCoords), "every remaining edge points at a written tile")

            // every tile gunzips, decodes and equals the live tile path for the same xyz
            val goldenFiles = GoldenTilesTest.all.associateBy { "${it.z}_${it.x}_${it.y}" }
            var goldenHits = 0
            db.prepare("SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles").use { stmt ->
                while (stmt.step()) {
                    val z = stmt.columnInt(0)
                    val x = stmt.columnInt(1)
                    val y = RegionExporter.xyzToTmsRow(z, stmt.columnInt(2))
                    val bytes = gunzip(stmt.columnBlob(3)!!)
                    val decoded = canonicalTile(decodeMvtToJson(bytes))
                    val live = encodeCanonicalTile(TileXyz(z, x, y))
                    assertEquals(live, decoded, "archive tile $z/$x/$y equals /v1/tile/$z/$x/$y")
                    goldenFiles["${z}_${x}_$y"]?.let { t ->
                        val committed = fixtures.readTable("geo/tiles/${t.z}_${t.x}_${t.y}.json").cases.single().expected.jsonObject
                        assertEquals(committed, decoded, "archive tile $t equals the committed golden tile")
                        goldenHits++
                    }
                }
            }
            assertTrue(goldenHits >= 5, "the archive shares at least a handful of tiles with the golden set ($goldenHits)")
        }
    }

    private suspend fun exportWorld(): File {
        assertIs<RegionExporter.ExportResult.Rendered>(exporter.exportNext(), "WORLD is the next stale region")
        assertIs<RegionExporter.ExportResult.NothingToDo>(exporter.exportNext())
        return archives(RegionExporter.WORLD_REGION_NAME).single()
    }

    @Test
    fun `exportNext without configured regions does nothing and a held lock yields LockBusy`() = runTest {
        assertIs<RegionExporter.ExportResult.NothingToDo>(exporter(emptyList()).exportNext())
        val other = DistributedLock(lockFile = lock.lockFile)
        assertTrue(other.tryAcquireLock())
        try {
            assertIs<RegionExporter.ExportResult.LockBusy>(exporter.exportNext())
            assertEquals(emptyList(), archives(regionName))
        } finally {
            other.tryClearLock()
        }
    }

    @Serializable
    data class StampInput(val op: String, val a: String, val b: String? = null)

    /**
     * P-RE-4: the filesystem-safe stem `yyyy-MM-ddTHH-mm-ss` is server-local time without an
     * offset, so it is interpreted in the same zone; a nonexistent spring-forward time and an
     * ambiguous fall-back time resolve the way kotlinx-datetime resolves them. `archivesForRegion`
     * matches on `startsWith(regionName)`, so `WA` also sees `WA_SOUND_*` archives, whose stems
     * then fail to parse and sort as DISTANT_PAST - pruning `WA` deletes them first.
     */
    @Test
    fun `P-RE-4 timestamp stems and archive names and prune order`() = runTest {
        val dir = TestEnv.tempDir("regions_stamps")
        val files = listOf(
            "WA_2026-01-01T00-00-00.mbtiles", "WA_2026-02-01T00-00-00.mbtiles", "WA_2026-03-01T00-00-00.mbtiles",
            "WA_SOUND_2026-04-01T00-00-00.mbtiles", "WA_not-a-stamp.mbtiles", "WAX_2026-05-01T00-00-00.mbtiles", "WA_2026-02-01T00-00-00.mbtiles.sha256",
        )
        files.forEach { File(dir, it).write("x") }
        val ex = exporter(emptyList(), dir)
        val inputs = listOf(
            StampInput("parseTimestampStem", "2026-01-15T13-45-30"),
            StampInput("parseTimestampStem", "2026-07-15T13-45-30"),
            StampInput("parseTimestampStem", "2026-03-08T02-30-00"),
            StampInput("parseTimestampStem", "2026-11-01T01-30-00"),
            StampInput("parseTimestampStem", "2026-01-15T13:45:30"),
            StampInput("parseTimestampStem", "garbage"),
            StampInput("parseArchiveTimestamp", "REGION_04", "REGION_04_2026-04-14T07-00-31.mbtiles"),
            StampInput("parseArchiveTimestamp", "WA", "WA_SOUND_2026-04-01T00-00-00.mbtiles"),
            StampInput("parseArchiveTimestamp", "WA", "WA_2026-02-01T00-00-00.mbtiles"),
            StampInput("archivesForRegion", "WA"),
            StampInput("archivesForRegion", "WA_SOUND"),
            StampInput("archivesForRegion", "NOPE"),
            StampInput("pruneOldArchives", "WA"),
        )
        fixtures.table(
            "regions/timestamps.json",
            FixtureHeader(
                pins = "RegionExporter stem parsing in TZ=America/Los_Angeles (yyyy-MM-ddTHH-mm-ss local -> RFC 3339 instant, null when unparsable), archive name parsing, archivesForRegion ordering (newest first, unparsable stems last) and pruneOldArchives keeping MAX_ARCHIVES=${RegionExporter.MAX_ARCHIVES} newest; the WA/WA_SOUND prefix overlap is pinned as is",
                decisions = listOf("RE-3", "RE-4"),
                generator = "RegionExportTest.P-RE-4",
            ),
            inputs,
        ) { input ->
            when (input.op) {
                "parseTimestampStem" -> ex.parseTimestampStem(input.a)?.toString()
                "parseArchiveTimestamp" -> ex.parseArchiveTimestamp(input.a, input.b!!)?.toString()
                "archivesForRegion" -> ex.archivesForRegion(input.a).joinToString(",") { it.name }
                "pruneOldArchives" -> {
                    ex.pruneOldArchives(input.a)
                    dir.listFiles(false).map { it.name }.sorted().joinToString(",")
                }
                else -> error(input.op)
            }
        }
        // behavioural: the stem written now parses back to now
        val now = Clock.System.now()
        val stamp = ex.currentTimestamp()
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}").matches(stamp), stamp)
        val parsed = assertNotNull(ex.parseTimestampStem(stamp))
        assertTrue((now - parsed).absoluteValue < kotlin.time.Duration.parse("2s"), "$stamp -> $parsed vs $now")
    }

    @Serializable
    data class ManifestInput(val scenario: String)

    /**
     * P-RE-5: `buildManifest()` reports every configured region; an unrendered one with nulls, a
     * rendered one with the newest archive, its size, the sidecar's sha256 (trusted as written) and
     * `createdAt` parsed from the stem; a missing or corrupt sidecar is recomputed and rewritten.
     */
    @Test
    fun `P-RE-5 manifest with and without archives and sidecar reuse`() = runTest {
        val dir = TestEnv.tempDir("regions_manifest")
        val coverage = "POLYGON ((-123 47, -122 47, -122 48, -123 48, -123 47))"
        val region = RegionExportConfig("MANIFEST_TEST", "manifest fixture", coverage)
        val ex = exporter(listOf(region), dir)
        val bogusSha = "0".repeat(64)

        fun scenario(name: String): RegionManifestEntry {
            dir.listFiles(false).forEach { it.deleteRecursively() }
            when (name) {
                "no archives" -> Unit
                "two archives, newest has a sidecar" -> {
                    File(dir, "MANIFEST_TEST_2026-01-02T03-04-05.mbtiles").write("older")
                    File(dir, "MANIFEST_TEST_2026-02-03T04-05-06.mbtiles").write("newest archive bytes")
                    File(dir, "MANIFEST_TEST_2026-02-03T04-05-06.mbtiles.sha256").write("$bogusSha  MANIFEST_TEST_2026-02-03T04-05-06.mbtiles\n")
                }
                "archive without sidecar" -> File(dir, "MANIFEST_TEST_2026-02-03T04-05-06.mbtiles").write("newest archive bytes")
                "archive with a corrupt sidecar" -> {
                    File(dir, "MANIFEST_TEST_2026-02-03T04-05-06.mbtiles").write("newest archive bytes")
                    File(dir, "MANIFEST_TEST_2026-02-03T04-05-06.mbtiles.sha256").write("not a checksum\n")
                }
                else -> error(name)
            }
            return ex.buildManifest().single()
        }

        val table = fixtures.table(
            "regions/manifest.json",
            FixtureHeader(
                pins = "RegionManifestEntry per configured region: nulls until rendered; newest archive by stem with size, sha256 from the sidecar when it holds 64 hex chars (not verified against the file) else recomputed and the sidecar rewritten; createdAt is the stem in TZ=America/Los_Angeles; coverageGeo and labelPoint from the coverage WKT",
                decisions = listOf("RE-2"),
                generator = "RegionExportTest.P-RE-5",
            ),
            listOf("no archives", "two archives, newest has a sidecar", "archive without sidecar", "archive with a corrupt sidecar").map { ManifestInput(it) },
        ) { scenario(it.scenario) }

        val entries = table.cases.map { fixtures.json.decodeFromJsonElement(RegionManifestEntry.serializer(), it.expected) }
        assertNull(entries[0].archive); assertNull(entries[0].createdAt); assertNull(entries[0].archiveSize)
        assertEquals("MANIFEST_TEST_2026-02-03T04-05-06.mbtiles", entries[1].archive)
        assertEquals(bogusSha, entries[1].archiveSha256, "an existing sidecar is trusted")
        assertEquals("newest archive bytes".length.toLong(), entries[1].archiveSize)
        assertEquals(entries[1].createdAt, ex.parseTimestampStem("2026-02-03T04-05-06"))
        val real = sha256File(File(dir, "MANIFEST_TEST_2026-02-03T04-05-06.mbtiles"))
        assertEquals(real, entries[2].archiveSha256)
        assertEquals(real, entries[3].archiveSha256, "a corrupt sidecar is recomputed")
        assertEquals("$real  MANIFEST_TEST_2026-02-03T04-05-06.mbtiles\n", File(dir, "MANIFEST_TEST_2026-02-03T04-05-06.mbtiles.sha256").readContents(), "and rewritten")
    }

    @Test
    fun `clear deletes every rendered archive`() = runTest {
        val dir = TestEnv.tempDir("regions_clear")
        File(dir, "A_2026-01-01T00-00-00.mbtiles").write("x")
        File(dir, "A_2026-01-01T00-00-00.mbtiles.sha256").write("x")
        exporter(emptyList(), dir).clear()
        assertEquals(emptyList(), dir.listFiles(false))
    }
}
