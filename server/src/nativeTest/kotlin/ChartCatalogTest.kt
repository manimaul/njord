import io.madrona.njord.db.ChartDao
import io.madrona.njord.resources
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [ChartDao.listAsync], which backs `GET /v1/chart_catalog`.
 *
 * The catalog pages on `charts.name` - the primary key - so its cursor is a lexicographic string
 * rather than a monotonically increasing row id. The boundary row is the thing worth pinning down:
 * the query is `name >= cursor LIMIT PAGE_SIZE + 1`, so the extra row is reported as the page's
 * `nextName` and served as the *first* row of the following page. Off by one in either direction
 * and the catalog either skips a chart or lists one twice.
 *
 * Test charts are named with a lowercase prefix so they sort after real ENC cells (which are
 * uppercase, e.g. `US5WA22M.000`) - that keeps the assertions independent of whatever else the
 * development database happens to hold.
 */
class ChartCatalogTest {

    lateinit var ds: PgDataSource
    lateinit var chartDao: ChartDao

    private val prefix = "zz_chart_catalog_test_"

    /** Enough to spill past [ChartDao.PAGE_SIZE], so the fixture straddles one page boundary. */
    private val total = ChartDao.PAGE_SIZE + 5

    private fun nameAt(i: Int) = "$prefix${i.toString().padStart(4, '0')}.000"

    @BeforeTest
    fun beforeEach() {
        // ChartDao's default FeatureDao comes from Singletons, which reads the config file.
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
        ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:6432/s57server")
        chartDao = ChartDao(ds)
        runBlocking {
            cleanup()
            insertCharts()
        }
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

    private suspend fun insertCharts() {
        val values = (1..total).joinToString(",\n") { i ->
            val name = nameAt(i)
            "('$name', 1, '$name', '20240101', '20240101', 1, " +
                    "ST_GeomFromText('POLYGON((-1 -1, -1 1, 1 1, 1 -1, -1 -1))', 4326), '{}', '{}')"
        }
        ds.connection().use { conn ->
            conn?.statement(
                """
                INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt)
                VALUES
                $values;
                """.trimIndent()
            )?.execute()
        }
    }

    @Test
    fun `a full page reports the next chart name as its cursor`() {
        runBlocking {
            val page = chartDao.listAsync(nameAt(1)) ?: error("no catalog page")

            assertEquals(ChartDao.PAGE_SIZE, page.page.size)
            assertEquals(nameAt(1), page.page.first().name)
            assertEquals(nameAt(ChartDao.PAGE_SIZE), page.page.last().name)
            // The (PAGE_SIZE + 1)th row is not in the page - it is the cursor for the next one.
            assertEquals(nameAt(ChartDao.PAGE_SIZE + 1), page.nextName)
        }
    }

    @Test
    fun `paging the cursor walks every chart exactly once`() {
        runBlocking {
            val seen = mutableListOf<String>()
            var cursor: String? = nameAt(1)
            var pages = 0
            while (cursor != null && pages < 10) {
                val page = chartDao.listAsync(cursor) ?: error("no catalog page")
                seen.addAll(page.page.map { it.name }.filter { it.startsWith(prefix) })
                cursor = page.nextName
                pages++
            }

            assertEquals((1..total).map { nameAt(it) }, seen)
            assertEquals(seen.size, seen.distinct().size)
        }
    }

    @Test
    fun `the last page reports no cursor`() {
        runBlocking {
            val page = chartDao.listAsync(nameAt(total)) ?: error("no catalog page")

            assertEquals(listOf(nameAt(total)), page.page.map { it.name })
            assertNull(page.nextName)
        }
    }

    @Test
    fun `a null cursor starts from the first chart in the catalog`() {
        runBlocking {
            val page = chartDao.listAsync() ?: error("no catalog page")

            assertTrue(page.page.isNotEmpty())
            assertTrue(page.totalChartCount >= total)
            // '' sorts before every name, so the first page starts at the lowest one.
            val lowest = chartDao.listAsync("")?.page?.first()?.name
            assertEquals(lowest, page.page.first().name)
        }
    }
}
