import io.madrona.njord.db.ChartDao
import io.madrona.njord.resources
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers [ChartDao.editionsAsync], which backs `GET /v1/chart_editions`.
 *
 * The key it produces (`"<DSID_UPDN>:<DSID_UADT>:<DSID_ISDT>"`) has to line up with what enc_cron
 * rebuilds from NOAA's ENCProdCat_19115.xml - update number from the `<edition>` suffix, UADT
 * from the `revision` date, ISDT from the `publication` date. If it does not, every cell looks
 * stale and the whole catalog re-downloads nightly.
 *
 * `DSID_EDTN` is deliberately absent from the key: GDAL overwrites it with whatever the last
 * applied `.00N` update file carries, and NOAA ships `0` there for some cells. US1GC09M is a real
 * example - catalog edition `74.6`, GDAL reports EDTN 0 / UPDN 6.
 */
class ChartEditionsTest {

    lateinit var ds: PgDataSource
    lateinit var chartDao: ChartDao

    private val prefix = "chart_editions_test_"

    @BeforeTest
    fun beforeEach() {
        // ChartDao's default FeatureDao comes from Singletons, which reads the config file.
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
        ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:6432/s57server")
        chartDao = ChartDao(ds)
        runBlocking { cleanup() }
    }

    @AfterTest
    fun afterEach() {
        runBlocking { cleanup() }
    }

    private suspend fun cleanup() {
        ds.connection().use { conn ->
            conn?.statement("DELETE FROM charts WHERE name LIKE '$prefix%';")?.execute()
        }
    }

    private suspend fun insertChart(
        name: String,
        dsidProps: String,
        updated: String = "20251209",
        issued: String = "20260520",
    ) {
        ds.connection().use { conn ->
            conn?.statement(
                """
                INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt)
                VALUES ('$name', 1, '$name', '$updated', '$issued', 1,
                        ST_GeomFromText('POLYGON((-1 -1, -1 1, 1 1, 1 -1, -1 -1))', 4326),
                        '$dsidProps', '{}');
                """.trimIndent()
            )?.execute()
        }
    }

    @Test
    fun `key is UPDN colon UADT colon ISDT keyed by chart name`() {
        runBlocking {
            val name = "${prefix}US1GC09M.000"
            insertChart(name, """{"DSID_UPDN": "6"}""", updated = "20251209", issued = "20260520")

            assertEquals("6:20251209:20260520", chartDao.editionsAsync()?.editions?.get(name))
        }
    }

    @Test
    fun `an EDTN of zero does not affect the key`() {
        runBlocking {
            // The exact shape GDAL produces for US1GC09M once its .001-.006 updates are applied.
            val name = "${prefix}EDTN_ZERO.000"
            insertChart(name, """{"DSID_EDTN": "0", "DSID_UPDN": "6"}""")

            assertEquals("6:20251209:20260520", chartDao.editionsAsync()?.editions?.get(name))
        }
    }

    @Test
    fun `an update number of zero round trips`() {
        runBlocking {
            // Brand new editions carry UPDN 0 and must not be confused with a missing field.
            val name = "${prefix}US1EEZ1M.000"
            insertChart(name, """{"DSID_UPDN": "0"}""", updated = "20251209", issued = "20251209")

            assertEquals("0:20251209:20251209", chartDao.editionsAsync()?.editions?.get(name))
        }
    }

    @Test
    fun `charts missing any key part are omitted so enc_cron re-fetches them`() {
        runBlocking {
            val noUpdn = "${prefix}NO_UPDN.000"
            val noUpdated = "${prefix}NO_UPDATED.000"
            val noIssued = "${prefix}NO_ISSUED.000"
            insertChart(noUpdn, """{"DSID_EDTN": "44"}""")
            insertChart(noUpdated, """{"DSID_UPDN": "2"}""", updated = "")
            insertChart(noIssued, """{"DSID_UPDN": "2"}""", issued = "")

            val editions = chartDao.editionsAsync()?.editions
            assertNull(editions?.get(noUpdn))
            assertNull(editions?.get(noUpdated))
            assertNull(editions?.get(noIssued))
        }
    }

    @Test
    fun `every inserted chart appears exactly once`() {
        runBlocking {
            val names = (1..5).map { "${prefix}CELL$it.000" }
            names.forEachIndexed { i, n ->
                insertChart(n, """{"DSID_UPDN": "$i"}""", updated = "2024010$i", issued = "2024020$i")
            }

            val editions = chartDao.editionsAsync()?.editions.orEmpty()
            names.forEachIndexed { i, n ->
                assertEquals("$i:2024010$i:2024020$i", editions[n])
            }
        }
    }
}
