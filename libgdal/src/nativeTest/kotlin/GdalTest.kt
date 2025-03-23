import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GdalTest {

    @BeforeTest
    fun beforeAll() {
        Gdal.initialize()
    }

    @Test
    fun testGeometry() {
        val wkb = byteArrayOf(
            0, 0, 0, 0, 3, 0, 0, 0, 1, 0, 0, 0, 5, -64, 96, 15, -32, 0, 0, 0, 0, 64, 71, -108, 117, -93, 54, 79, 11,
            -64, 96, 10, 64, 0, 0, 0, 0, 64, 71, -108, 117, -93, 54, 79, 11, -64, 96, 10, 64, 0, 0, 0, 0, 64, 71,
            -93, -67, -58, -91, 17, 117, -64, 96, 15, -32, 0, 0, 0, 0, 64, 71, -93, -67, -58, -91, 17, 117, -64, 96,
            15, -32, 0, 0, 0, 0, 64, 71, -108, 117, -93, 54, 79, 11)
//        val wkb2 = byteArrayOf(
//            1, 3, 0, 0, 0, 1, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, -16, 94, -64, -72, 73, -66, -64, 67, 67, 85, -64,
//            0, 0, 0, 0, 0, -120, 93, -64, -72, 73, -66, -64, 67, 67, 85, -64, 0, 0, 0, 0, 0, -120, 93, -64, -72, 73,
//            -66, -64, 67, 67, 85, -64, 0, 0, 0, 0, 0, -16, 94, -64, -72, 73, -66, -64, 67, 67, 85, -64, 0, 0, 0, 0,
//            0, -16, 94, -64, -72, 73, -66, -64, 67, 67, 85, -64
//        )
        val geo = OgrGeometry.fromWkb(wkb)
        assertNotNull(geo)
        val geoJson = geo.geoJson()
        assertNotNull(geoJson)
    }

    @Test
    fun testPointGeometry() {
        val wkt = "POINT (10 20)"
        val geo = OgrGeometry.fromWkt(wkt)
        assertNotNull(geo)
        val geoJson = geo.geoJson()
        assertNotNull(geoJson)
        assertEquals(geoJson.coordinates, listOf(10.0, 20.0))
    }
}