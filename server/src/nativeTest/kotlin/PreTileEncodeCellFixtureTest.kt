import io.madrona.njord.Singletons
import io.madrona.njord.ingest.ChartIngest
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-SY-2: `preTileEncode` over every real feature of the fixture cell, grouped by layer, as
 * `testdata/symbology/pre_tile_encode_cell/<LAYER>.json`. Where P-SY-1 sweeps attributes one and
 * two at a time, this is the combination of attributes a NOAA cell actually carries, and the
 * props are exactly as GDAL maps them (list attributes as arrays, reals as doubles, `METERS` on
 * soundings), so the file also documents the input shape the port's reader must produce.
 *
 * TOPMAR features get no association here (that needs the database, see P-DB-4), so they take
 * the RIGID branch.
 */
class PreTileEncodeCellFixtureTest {

    @BeforeTest
    fun before() {
        TestEnv.init()
        Gdal.initialize()
    }

    @Test
    fun everyFeatureOfTheCell() {
        val cell = fixtures.file("fixtures/US5WA22M/US5WA22M.000")
        assertTrue(cell.exists(), "missing $cell")
        val ds = OgrS57Dataset(cell)
        val layers = ds.layerNames().filter { it !in ChartIngest.exLayers }.sorted()
        var total = 0
        layers.forEach { layer ->
            val inputs = ds.getLayer(layer)!!.mapFeature { feature ->
                SymbologyInput(
                    props = feature.properties,
                    geometry_type = feature.geometry?.type?.name?.uppercase() ?: "NONE",
                )
            }
            if (inputs.isEmpty()) return@forEach
            total += inputs.size
            fixtures.table(
                "symbology/pre_tile_encode_cell/$layer.json",
                FixtureHeader(
                    pins = "preTileEncode over every $layer feature of US5WA22M: props exactly as GDAL maps them in, symbology props out",
                    decisions = listOf("SY-10"),
                    generator = "PreTileEncodeCellFixtureTest.everyFeatureOfTheCell",
                    source = "fixtures/US5WA22M/US5WA22M.000 (edition 40, update 4) read with GDAL ${Gdal.gdalVersion()}",
                ),
                inputs,
            ) { input ->
                val feature = ChartFeature(layer, null, input.props.toMutableMap(), emptyList())
                runBlocking { Singletons.layerFactory.preTileEncode(feature) }
                JsonObject(feature.props)
            }
        }
        assertTrue(total > 5000, "expected the whole cell, got $total features")
    }
}
