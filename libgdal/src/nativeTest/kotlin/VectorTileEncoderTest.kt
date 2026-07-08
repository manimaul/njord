import io.madrona.njord.geojson.Position
import kotlinx.serialization.json.JsonPrimitive
import tile.VectorTileEncoder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VectorTileEncoderTest {

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
    }

    @Test
    fun `hasFeatures is false for a fresh encoder`() {
        val encoder = VectorTileEncoder()
        assertFalse(encoder.hasFeatures())
    }

    @Test
    fun `hasFeatures excludes only the named layer`() {
        val encoder = VectorTileEncoder()
        encoder.addFeature("PLY", emptyMap(), Gdal.createPoint(Position(100.0, 100.0)))

        assertFalse(encoder.hasFeatures(excludingLayers = setOf("PLY")))
        assertTrue(encoder.hasFeatures())
    }

    @Test
    fun `hasFeatures is true when a non-excluded layer has features`() {
        val encoder = VectorTileEncoder()
        encoder.addFeature("PLY", emptyMap(), Gdal.createPoint(Position(100.0, 100.0)))
        encoder.addFeature("DEPARE", mapOf("DRVAL1" to JsonPrimitive(0)), Gdal.createPoint(Position(200.0, 200.0)))

        assertTrue(encoder.hasFeatures(excludingLayers = setOf("PLY")))
    }
}
