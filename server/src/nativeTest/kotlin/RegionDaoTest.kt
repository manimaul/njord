import io.madrona.njord.db.RegionDao
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P-DB-6 / DB-11: the region export flags the worker and the chart delete path rely on -
 * `regionNeedsRebuild` compares `charts.ingested_at` against `region_export_state.exported_at`,
 * `markRegionExported` / `clearRegionExportState` move that watermark, and
 * `regionsContainingChart` is what ChartHandler asks *before* deleting so the right archives are
 * invalidated.
 */
class RegionDaoTest {

    lateinit var regionDao: RegionDao

    private val chartNamePrefix = "f2_region_dao_test_chart_"
    private val regionNamePrefix = "f2_region_dao_test_region_"

    @BeforeTest
    fun beforeEach() {
        TestEnv.init()
        regionDao = RegionDao(TestEnv.ds)
        runBlocking { cleanup() }
    }

    @AfterTest
    fun afterEach() {
        runBlocking { cleanup() }
    }

    private suspend fun cleanup() {
        TestEnv.ds.connection().use { conn ->
            conn?.statement("DELETE FROM charts WHERE name LIKE '$chartNamePrefix%';")?.execute()
            conn?.statement("DELETE FROM region_export_state WHERE region_name LIKE '$regionNamePrefix%';")?.execute()
        }
    }

    private suspend fun insertTestChart(name: String, wkt: String) {
        TestEnv.ds.connection().use { conn ->
            conn?.statement(
                """
                INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt)
                VALUES ('$name', 1, '$name.000', '20240101', '20240101', 1, ST_GeomFromText('$wkt', 4326), '{}', '{}');
                """.trimIndent()
            )?.execute()
        }
    }

    @Test
    fun `regionNeedsRebuild reflects per-region chart ingestion recency`() = runBlocking {
        val regionA = "${regionNamePrefix}A"
        val regionB = "${regionNamePrefix}B"
        val coverageA = "POLYGON((-10 -10, -10 10, 10 10, 10 -10, -10 -10))"
        val coverageB = "POLYGON((20 20, 20 30, 30 30, 30 20, 20 20))"

        // No charts intersect either region yet.
        assertEquals(false, regionDao.regionNeedsRebuild(coverageA, regionA))
        assertEquals(false, regionDao.regionNeedsRebuild(coverageB, regionB))

        // A chart lands inside region A only.
        insertTestChart("${chartNamePrefix}a", "POINT(0 0)")
        assertEquals(true, regionDao.regionNeedsRebuild(coverageA, regionA))
        assertEquals(false, regionDao.regionNeedsRebuild(coverageB, regionB))

        // Exporting region A clears its rebuild flag; region B is unaffected (and still empty).
        regionDao.markRegionExported(regionA)
        assertEquals(false, regionDao.regionNeedsRebuild(coverageA, regionA))
        assertEquals(false, regionDao.regionNeedsRebuild(coverageB, regionB))

        // A new chart intersecting only region B flips B, but not the already-exported region A.
        insertTestChart("${chartNamePrefix}b", "POINT(25 25)")
        assertEquals(false, regionDao.regionNeedsRebuild(coverageA, regionA))
        assertEquals(true, regionDao.regionNeedsRebuild(coverageB, regionB))
    }

    @Test
    fun `clearRegionExportState forces an exported region stale again`() = runBlocking {
        val region = "${regionNamePrefix}clear"
        val coverage = "POLYGON((-10 -10, -10 10, 10 10, 10 -10, -10 -10))"

        insertTestChart("${chartNamePrefix}clear", "POINT(0 0)")
        regionDao.markRegionExported(region)
        assertEquals(false, regionDao.regionNeedsRebuild(coverage, region))

        assertEquals(true, regionDao.clearRegionExportState(region))
        assertEquals(true, regionDao.regionNeedsRebuild(coverage, region))

        // Clearing a region with no recorded export reports that nothing was deleted.
        assertEquals(false, regionDao.clearRegionExportState(region))
    }

    @Test
    fun `regionsContainingChart lists only the regions whose coverage the chart intersects`() = runBlocking {
        val chart = "${chartNamePrefix}contained"
        insertTestChart(chart, "POLYGON((0 0, 0 2, 2 2, 2 0, 0 0))")
        val regions = listOf(
            "${regionNamePrefix}inside" to "POLYGON((-10 -10, -10 10, 10 10, 10 -10, -10 -10))",
            "${regionNamePrefix}touching" to "POLYGON((2 0, 2 5, 5 5, 5 0, 2 0))",
            "${regionNamePrefix}elsewhere" to "POLYGON((20 20, 20 30, 30 30, 30 20, 20 20))",
            "${regionNamePrefix}world" to "POLYGON((-180 -90, 180 -90, 180 90, -180 90, -180 -90))",
        )
        assertEquals(
            listOf("${regionNamePrefix}inside", "${regionNamePrefix}touching", "${regionNamePrefix}world"),
            regionDao.regionsContainingChart(chart, regions),
            "ST_Intersects: shared boundary counts, disjoint does not; order follows the input",
        )
        assertEquals(emptyList(), regionDao.regionsContainingChart("${chartNamePrefix}unknown", regions))
        assertEquals(emptyList(), regionDao.regionsContainingChart(chart, emptyList()))
    }

    @Test
    fun `findChartsInRegion and latestChartUpdateInRegion see only intersecting charts`() = runBlocking {
        insertTestChart("${chartNamePrefix}r1", "POINT(0 0)")
        insertTestChart("${chartNamePrefix}r2", "POINT(1 1)")
        insertTestChart("${chartNamePrefix}far", "POINT(50 50)")
        val coverage = "POLYGON((-10 -10, -10 10, 10 10, 10 -10, -10 -10))"
        val charts = regionDao.findChartsInRegion(coverage)!!.filter { it.name.startsWith(chartNamePrefix) }
        assertEquals(listOf("${chartNamePrefix}r1", "${chartNamePrefix}r2"), charts.map { it.name }, "ordered by name")
        assertEquals("20240101", regionDao.latestChartUpdateInRegion(coverage))
        assertEquals("", regionDao.latestChartUpdateInRegion("POLYGON((80 80, 80 85, 85 85, 85 80, 80 80))"), "MAX over nothing reads back as the empty string")
    }
}
