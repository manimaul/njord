import kotlin.test.Test
import kotlin.test.assertTrue

class OgrShapefileDatasetTest {

    @Test
    fun testReadShapefile() {
        Gdal.initialize()

        val file = File("./src/nativeTest/resources/ne_110_extract/ne_110m_coastline.shp")
        val shapeFile = OgrShapefileDataset(file)

        val layers = shapeFile.layerNames()
        assertTrue(layers.size > 0)
    }
}
