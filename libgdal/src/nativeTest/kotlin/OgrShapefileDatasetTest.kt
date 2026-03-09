import kotlin.test.Test
import kotlin.test.assertTrue

class OgrShapefileDatasetTest {

    @Test
    fun testReadShapefile() {
        Gdal.initialize()
        val path = "../data/ne/110m/extract/ne_110m_coastline.shp"
        val shapeFile = OgrShapefileDataset(File(path))

        val layers = shapeFile.layerNames()
        assertTrue(layers.size > 0)
    }
}
