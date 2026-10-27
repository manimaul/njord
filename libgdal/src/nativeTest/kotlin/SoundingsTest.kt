import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S-57 soundings are encoded to one decimal place in metres, and the metres style splits that
 * into a whole-metre label and a tenths subscript (`METERS_W` / `METERS_T`). The split must
 * therefore reproduce the decimal the chart encoded, not the nearest binary double - `2.3` is
 * `2.29999…` as a double, and truncating `(2.3 - 2) * 10` gives tenths = 2.
 */
class SoundingsTest {

    private fun split(meters: Double) = displayDepths(meters).let { it.metersWhole to it.metersTenths }

    @Test
    fun `tenths reproduce the encoded decimal`() {
        assertEquals(2 to 2, split(2.29999))
        assertEquals(2 to 3, split(2.3))
        assertEquals(7 to 6, split(7.6))
        assertEquals(0 to 1, split(0.1))
        assertEquals(0 to 7, split(0.7))
        assertEquals(1 to 1, split(1.1))
    }

    @Test
    fun `every tenth from 0 to 100 metres splits exactly`() {
        for (i in 0..1000) {
            val meters = i / 10.0
            assertEquals(i / 10 to i % 10, split(meters), "meters=$meters")
        }
    }

    @Test
    fun `whole metres have zero tenths`() {
        assertEquals(0 to 0, split(0.0))
        assertEquals(10 to 0, split(10.0))
        assertEquals(31 to 0, split(31.0))
    }

    @Test
    fun `extra precision is truncated not rounded so the label never reads deeper than the sounding`() {
        assertEquals(4 to 3, split(4.35))
        assertEquals(4 to 3, split(4.39))
        assertEquals(9 to 9, split(9.99))
    }

    @Test
    fun `drying heights keep their sign on both parts`() {
        // Existing behaviour, pinned so the tenths fix does not change it.
        assertEquals(-1 to -5, split(-1.5))
        assertEquals(0 to -3, split(-0.3))
    }

    @Test
    fun `feet and fathoms are unchanged`() {
        val d = displayDepths(7.6)
        assertEquals(25, d.feet)        // 24.93 ft rounds up past .9
        assertEquals(4, d.fathoms)      // 4.16 fm
        assertEquals(0, d.fathomsFeet)  // 0.93 ft of the partial fathom
        assertEquals(7.6, d.meters)
    }
}
