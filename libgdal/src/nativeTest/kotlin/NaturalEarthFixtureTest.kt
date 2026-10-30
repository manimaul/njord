import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.Serializable
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P-IN-6 (shapefile half) `s57/natural_earth.json`: what `OgrShapefileDataset` yields for the
 * Natural Earth coastline extract `NaturalEarthIngest` would insert as base features. The
 * `shapefile`/`dbase` crates (or the `gdal` crate) must produce the same layer names, count and
 * field mapping. Layer name normalisation and scale derivation live in the server module and are
 * pinned there.
 */
class NaturalEarthFixtureTest {

    @Serializable
    data class ShapefileView(val layers: List<String>, val featureCount: Long, val firstFeatures: Map<String, List<FeatureOut>>)

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
    }

    @Test
    fun `natural earth extract reads the same way as a NOAA cell`() {
        val table = fixtures.table(
            "s57/natural_earth.json",
            gdalHeader(
                pins = "OgrShapefileDataset: layer names (one per .shp), total feature count, and the first three features' GDAL field mapping plus WKT after makeValid() - the shape NaturalEarthIngest inserts into base_features",
                decisions = listOf("IN-8"),
                generator = "NaturalEarthFixtureTest",
                source = "fixtures/ne_110_extract/ne_110m_coastline.shp (Natural Earth 1:110m coastline)",
            ),
            listOf("fixtures/ne_110_extract/ne_110m_coastline.shp"),
        ) { path ->
            val file = fixtures.file(path)
            assertTrue(file.exists(), "missing fixture $file")
            val ds = OgrShapefileDataset(file)
            ShapefileView(
                layers = ds.layerNames(),
                featureCount = ds.featureCount(),
                firstFeatures = ds.layerNames().associateWith { ds.getLayer(it)!!.insertedFeatures().take(3) },
            )
        }
        assertEquals(1, table.cases.size)
    }
}
