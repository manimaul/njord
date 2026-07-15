import io.madrona.njord.db.RegionDao
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionDaoTest {

    lateinit var ds: PgDataSource
    lateinit var regionDao: RegionDao

    private val chartNamePrefix = "region_dao_test_chart_"
    private val regionNamePrefix = "region_dao_test_region_"

    @BeforeTest
    fun beforeEach() {
        ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:6432/s57server")
        regionDao = RegionDao(ds)
        runBlocking { cleanup() }
    }

    @AfterTest
    fun afterEach() {
        runBlocking { cleanup() }
    }

    private suspend fun cleanup() {
        ds.connection().use { conn ->
            conn?.statement("DELETE FROM charts WHERE name LIKE '$chartNamePrefix%';")?.execute()
            conn?.statement("DELETE FROM region_export_state WHERE region_name LIKE '$regionNamePrefix%';")?.execute()
        }
    }

    private suspend fun insertTestChart(name: String, wkt: String) {
        ds.connection().use { conn ->
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
}
