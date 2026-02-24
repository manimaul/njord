import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OgrS57DatasetTest {

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
    }

    @Test
    fun testDataSet() {
        val file = File("./src/nativeTest/resources/US5WA22M/US5WA22M.000")
        assertTrue(file.exists())
        val ds = OgrS57Dataset(file)
        assertEquals(67, ds.layerCount)
        assertEquals(5164, ds.featureCount())

        val soundg = ds.getLayer("SOUNDG")
        assertNotNull(soundg)
        assertEquals("SOUNDG", soundg.name)
        val sfc = soundg.geoJson()
        assertNotNull(sfc)

        val lndare = ds.getLayer("LNDARE")
        assertNotNull(lndare)
        assertEquals("LNDARE", lndare.name)
        val fc = lndare.geoJson()
        assertNotNull(fc)
    }
}