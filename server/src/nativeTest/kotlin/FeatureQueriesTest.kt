import io.madrona.njord.Singletons
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P-DB-3 / DB-4: the feature query contract behind the tile path. `findAllChartFeaturesAsync4326`
 * clips every feature of the named charts to the tile box, `findChartFeaturesAsync4326` to the
 * include mask (box clip for a single ring, true intersection otherwise), both filtered by
 * `z_min <= z <= z_max`; an empty chart list answers an empty map without a query. Rows are
 * recorded as (layer, props, WKT after clipping), canonically sorted, for the tiles of P-TE-5.
 */
class FeatureQueriesTest {

    private lateinit var chartDao: ChartDao

    @BeforeTest
    fun before() {
        TestCharts.ensureSeeded()
        chartDao = ChartDao(TestEnv.ds, FeatureDao(TestEnv.ds))
    }

    @Serializable
    data class QueryInput(val query: String, val tile: TileXyz, val charts: List<String>)

    @Serializable
    data class Row(val chart: String, val layer: String, val props: JsonObject, val assoc: List<String>, val wkt: String)

    private fun ChartFeature.row(chart: String) = Row(
        chart = chart,
        layer = layer,
        props = JsonObject(props).sortedKeys(),
        assoc = associatedLayerNames.sorted(),
        wkt = geomWKB?.let { OgrGeometry.fromWkb4326(it)?.wkt } ?: "",
    )

    private val rowOrder = compareBy<Row>({ it.chart }, { it.layer }, { it.props["LNAM"]?.toString() ?: "" }, { it.props.toString() }, { it.wkt })

    private suspend fun run(input: QueryInput): List<Row> {
        val ts = Singletons.tileSystem
        return when (input.query) {
            "findAllChartFeaturesAsync4326" -> {
                val tileWkb = ts.createTileClipPolygon(input.tile.x, input.tile.y, input.tile.z, expandPixels = 15).wkb
                chartDao.findAllChartFeaturesAsync4326(tileWkb, input.charts, input.tile.z)!!
                    .flatMap { (chart, features) -> features.map { it.row(chart) } }
            }

            "findChartFeaturesAsync4326" -> {
                val include = ts.createTileClipPolygon(input.tile.x, input.tile.y, input.tile.z, expandPixels = 15).wkb
                chartDao.findChartFeaturesAsync4326(include, input.charts.single(), input.tile.z)!!.map { it.row(input.charts.single()) }
            }

            else -> error(input.query)
        }.sortedWith(rowOrder)
    }

    @Test
    fun `P-DB-3 feature query rows for tiles of the golden set`() = runTest {
        val inputs = listOf(
            QueryInput("findAllChartFeaturesAsync4326", TileXyz(15, 5238, 11488), listOf(TestCharts.WA)),
            QueryInput("findChartFeaturesAsync4326", TileXyz(15, 5238, 11488), listOf(TestCharts.WA)),
            QueryInput("findAllChartFeaturesAsync4326", TileXyz(14, 2594, 5762), listOf(TestCharts.OUTER, TestCharts.INNER)),
            QueryInput("findChartFeaturesAsync4326", TileXyz(14, 2594, 5762), listOf(TestCharts.OUTER)),
            // at z10 the chart is not eligible for the tile, but the query itself only applies z_min/z_max
            QueryInput("findAllChartFeaturesAsync4326", TileXyz(10, 163, 359), listOf(TestCharts.WA)),
        )
        val table = fixtures.table(
            "db/feature_queries.json",
            FixtureHeader(
                pins = "ChartDao.findAllChartFeaturesAsync4326 (ST_ClipByBox2D to the 15px-expanded tile box, chart_name = ANY) and findChartFeaturesAsync4326 (box clip for a one-ring include, else ST_Intersection) with z_min <= z <= z_max; rows as (chart, layer, props, TOPMAR assoc, WKT) sorted by chart, layer, LNAM, props, wkt",
                decisions = listOf("DB-4", "DB-5"),
                generator = "FeatureQueriesTest",
                source = goldenSource(),
            ),
            inputs,
        ) { runBlocking { run(it) } }
        assertTrue(table.cases.all { (it.expected as kotlinx.serialization.json.JsonArray).isNotEmpty() })
    }

    @Test
    fun `an empty chart list is an empty map and the zoom range filter applies at both ends`() = runTest {
        assertEquals(emptyMap(), chartDao.findAllChartFeaturesAsync4326(ByteArray(0), emptyList(), 14))
        val tile = Singletons.tileSystem.createTileClipPolygon(2619, 5744, 14, expandPixels = 15).wkb
        val z14 = chartDao.findAllChartFeaturesAsync4326(tile, listOf(TestCharts.WA), 14)!![TestCharts.WA]!!.size
        val z10 = chartDao.findAllChartFeaturesAsync4326(tile, listOf(TestCharts.WA), 10)!![TestCharts.WA]!!.size
        val z32 = chartDao.findAllChartFeaturesAsync4326(tile, listOf(TestCharts.WA), 32)!![TestCharts.WA]!!.size
        val z33 = chartDao.findAllChartFeaturesAsync4326(tile, listOf(TestCharts.WA), 33)?.get(TestCharts.WA)?.size ?: 0
        assertTrue(z10 < z14, "features with MINZ above 10 drop out: $z10 < $z14")
        assertTrue(z32 >= z14 && z33 == 0, "MAXZ defaults to 32 inclusive: z32=$z32 z33=$z33")
    }
}
