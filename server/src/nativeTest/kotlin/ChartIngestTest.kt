import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.ingest.ChartIngest
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import io.madrona.njord.util.DistributedLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The S-57 -> PostGIS pipeline end to end, through the production [ChartIngest] (audit IN-1,
 * IN-3, IN-4, IN-5), and the DB contract it leaves behind (P-IN-5, `s57/.../db_state.json`): the
 * `charts` row and the per-layer aggregates the Rust ingest must reproduce - `z_min`/`z_max` from
 * `MINZ`/`MAXZ` with the 0/32 defaults, `lnam_refs` mirrored from `LNAM_REFS`, geometry forced 2D.
 *
 * The destructive cases (overwrite, abort) run against the synthetic cells and re-seed afterwards,
 * so the golden tile tests see the same database whatever the test order.
 */
class ChartIngestTest {

    private lateinit var chartDao: ChartDao
    private lateinit var featureDao: FeatureDao

    @BeforeTest
    fun before() {
        TestEnv.init()
        Gdal.initialize()
        featureDao = FeatureDao(TestEnv.ds)
        chartDao = ChartDao(TestEnv.ds, featureDao)
    }

    @AfterTest
    fun after() = TestCharts.ensureSeeded()

    private suspend fun deleteSynthetic() {
        chartDao.deleteByNameAsync(TestCharts.OUTER)
        chartDao.deleteByNameAsync(TestCharts.INNER)
    }

    @Test
    fun `IN-1 a NOAA cell zip is unzipped then read by GDAL and installed with every feature`() = runTest {
        chartDao.deleteByNameAsync(TestCharts.WA)
        val ingest = TestIngest("in1")
        val upload = ingest.zipCellDir("fixtures/US5WA22M", "US5WA22M.zip")
        val s57 = OgrS57Dataset(fixtures.file("fixtures/US5WA22M/US5WA22M.000"))
        val expectedFeatures = s57.featureCount(ChartIngest.exLayers)
        val geometrylessFeatures = s57.layerNames().filter { it !in ChartIngest.exLayers }.sumOf { layer ->
            s57.getLayer(layer)!!.mapFeature { it.geometry == null }.count { it }.toLong()
        }

        ingest.ingestCharts(upload)

        val chart = assertNotNull(chartDao.findAsync(TestCharts.WA))
        assertEquals(15000, chart.scale)
        assertEquals(14, chart.zoom)
        assertEquals("US5WA22M.000", chart.fileName)
        assertEquals("4", chart.dsidProps["DSID_UPDN"].toString().trim('"'), "GDAL applied .001-.004")
        assertTrue("DSID" !in chart.layers && "M_COVR" in chart.layers, chart.layers.toString())

        val report = assertIs<WsMsg.CompletionReport>(ingest.status())
        assertEquals(1, report.totalChartCount)
        assertEquals(expectedFeatures, report.totalFeatureCount, "the pre-count is GDAL's featureCount over the non-meta layers")
        // S-57 collection objects (C_AGGR, C_ASSO) carry no geometry and are skipped at insert, so the
        // installed count is below the pre-count by exactly those features; the report counts what landed.
        val installed = report.items.single { it.chartName == TestCharts.WA }.featureCount
        assertEquals(installed.toInt(), chart.featureCount)
        assertEquals(expectedFeatures - installed, geometrylessFeatures, "only geometry-less features are skipped")
        assertEquals(emptyList(), report.failedCharts)

        assertTrue(ingest.lockFile.isEmpty(), "the lock is released after ingest")
        assertTrue(!ingest.chartDir.exists(), "the claimed work directory is deleted")
        assertTrue(ingest.tileCache.counts().isEmpty(), "the tile cache was invalidated")
    }

    @Test
    fun `IN-3 re-ingesting a cell leaves exactly one chart row and one set of features`() = runTest {
        val first = TestIngest("in3a")
        first.ingestCharts(first.zipCellDir("fixtures/synthetic", "synthetic.zip"))
        val before = assertNotNull(chartDao.findAsync(TestCharts.INNER))

        val second = TestIngest("in3b")
        second.ingestCharts(second.zipCellDir("fixtures/synthetic", "synthetic.zip"))
        val after = assertNotNull(chartDao.findAsync(TestCharts.INNER))

        assertEquals(before.featureCount, after.featureCount, "no duplicates and nothing left over")
        assertEquals(1, countCharts(TestCharts.INNER))
        assertEquals(after.featureCount, countFeatures(TestCharts.INNER))
    }

    /**
     * Clearing the lock from another instance is the operator's abort (`DELETE /v1/enc_save`).
     * The chart whose features were being inserted is rolled back to zero features; the chart row
     * itself was committed by `insertAsync` before the feature transaction began and survives
     * (Kotlin behaviour, kept - D5 asks the port to make the install one transaction).
     *
     * The completion message is `Idle` but `IngestStatus.writeMsg` only writes while the lock is
     * held, so the status file keeps the last progress report rather than announcing completion.
     */
    @Test
    fun `IN-4 clearing the lock mid-ingest rolls the chart's features back`() = runTest {
        chartDao.deleteByNameAsync(TestCharts.WA)
        val ingest = TestIngest("in4")
        val upload = ingest.zipCellDir("fixtures/US5WA22M", "US5WA22M.zip")
        check(ingest.acquire())

        val job = async(Dispatchers.Default) { ingest.chartIngest().ingest(upload) }
        // wait until feature inserts are under way, then clear the lock from "another process"
        withTimeout(60_000) {
            while ((ingest.status() as? WsMsg.Info)?.let { it.feature > 0 } != true) delay(20)
        }
        val other = DistributedLock(lockFile = ingest.lockFile)
        assertTrue(other.tryClearLock())
        job.await()

        assertEquals(0, countFeatures(TestCharts.WA), "the feature transaction was rolled back")
        assertNotNull(chartDao.findAsync(TestCharts.WA), "the chart row was committed separately and survives")
        assertIs<WsMsg.Info>(ingest.status(), "no completion report is written without the lock")
        assertTrue(ingest.lockFile.isEmpty())
        assertTrue(!ingest.chartDir.exists())
    }

    @Test
    fun `IN-5 unzip skips __MACOSX and directories and ingests only the 000 files`() = runTest {
        deleteSynthetic()
        val ingest = TestIngest("in5")
        val outer = fixtures.file("fixtures/synthetic/US4ZZ01M.000")
        val inner = fixtures.file("fixtures/synthetic/US5ZZ02M.000")
        val upload = ingest.zip(
            "mixed.zip",
            mapOf(
                "ENC_ROOT/US4ZZ01M/US4ZZ01M.000" to outer,
                "ENC_ROOT/US5ZZ02M/US5ZZ02M.000" to inner,
                "__MACOSX/ENC_ROOT/US4ZZ01M/._US4ZZ01M.000" to outer,
                "ENC_ROOT/README.TXT" to fixtures.file("fixtures/US5WA22M/US5WA22A.TXT"),
            ),
            directories = listOf("ENC_ROOT/", "ENC_ROOT/US4ZZ01M/"),
        )

        ingest.ingestCharts(upload)

        val report = assertIs<WsMsg.CompletionReport>(ingest.status())
        assertEquals(2, report.totalChartCount)
        assertEquals(setOf(TestCharts.OUTER, TestCharts.INNER), report.items.map { it.chartName }.toSet())
        assertEquals(6L, report.items.first { it.chartName == TestCharts.OUTER }.featureCount)
        assertEquals(8L, report.items.first { it.chartName == TestCharts.INNER }.featureCount)
        assertEquals(emptyList(), report.failedCharts)
    }

    @Serializable
    data class LayerAggregate(val layer: String, val count: Long, val minZ: Int, val maxZ: Int, val withLnamRefs: Long)

    @Serializable
    data class DbState(
        val name: String,
        val scale: Int,
        val fileName: String,
        val updated: String,
        val issued: String,
        val zoom: Int,
        val covrWkt: String,
        val dsidProps: JsonElement,
        val chartTxtKeys: List<String>,
        val layers: List<LayerAggregate>,
    )

    @Test
    fun `P-IN-5 the charts row and per-layer aggregates after ingest`() = runTest {
        TestCharts.ensureSeeded()
        val header = FixtureHeader(
            pins = "DB contract after ingest: charts row (ingested_at excluded) and per layer count, min(z_min), max(z_max), count(lnam_refs); MINZ/MAXZ default to 0/32, LNAM_REFS mirrored into lnam_refs, geometry forced 2D",
            decisions = listOf("DB-6", "IN-1", "D7"),
            generator = "ChartIngestTest.P-IN-5",
            source = "fixtures/US5WA22M (edition 40 update 4), fixtures/synthetic; GDAL ${Gdal.gdalVersion()}",
        )
        fixtures.table("s57/US5WA22M/db_state.json", header, listOf(TestCharts.WA)) { runBlocking { dbState(it) } }
        fixtures.table("s57/synthetic/db_state.json", header, listOf(TestCharts.OUTER, TestCharts.INNER)) { runBlocking { dbState(it) } }
        Unit
    }

    private suspend fun dbState(name: String): DbState = TestEnv.ds.connection()!!.use { conn ->
        val chart = conn.prepareStatement(
            "SELECT name, scale, file_name, updated, issued, zoom, st_astext(covr), dsid_props::text, chart_txt::text FROM charts WHERE name = $1;"
        ).apply { setString(1, name) }.executeQuery().use { rs ->
            check(rs.next()) { "no chart $name" }
            val txt = Json.parseToJsonElement(rs.getString(9)) as JsonObject
            DbState(
                name = rs.getString(1),
                scale = rs.getInt(2),
                fileName = rs.getString(3),
                updated = rs.getString(4),
                issued = rs.getString(5),
                zoom = rs.getInt(6),
                covrWkt = rs.getString(7),
                dsidProps = Json.parseToJsonElement(rs.getString(8)),
                chartTxtKeys = txt.keys.sorted(),
                layers = emptyList(),
            )
        }
        val layers = conn.prepareStatement(
            """
            SELECT layer, count(*), min(z_min), max(z_max), count(lnam_refs), count(*) FILTER (WHERE st_ndims(geom) <> 2)
            FROM features WHERE chart_name = $1 GROUP BY layer ORDER BY layer;
            """.trimIndent()
        ).apply { setString(1, name) }.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    check(rs.getLong(6) == 0L) { "3D geometry stored for ${rs.getString(1)}" }
                    add(LayerAggregate(rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getInt(4), rs.getLong(5)))
                }
            }
        }
        chart.copy(layers = layers)
    }

    private suspend fun countCharts(name: String): Int = TestEnv.ds.connection()!!.use { conn ->
        conn.prepareStatement("SELECT count(*) FROM charts WHERE name = $1;").apply { setString(1, name) }
            .executeQuery().use { if (it.next()) it.getInt(1) else 0 }
    }

    private suspend fun countFeatures(name: String): Int = TestEnv.ds.connection()!!.use { conn ->
        FeatureDao(TestEnv.ds).featureCount(conn, name)
    }
}
