import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TileSystemTest {

    val subject: TileSystem = TileSystem()

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
    }

    @Test
    fun createTileClipPolygon() {
        val z = 16
        val x = 10485
        val y = 22975
        val polygon = subject.createTileClipPolygon(x, y, z)
        val bounds = polygon.envelope()
        assertEquals(47.28295557691229, bounds.north)
        assertEquals(-122.4041748046875, bounds.west)
        assertEquals(47.27922900257082, bounds.south)
        assertEquals(-122.398681640625, bounds.east)
    }
}