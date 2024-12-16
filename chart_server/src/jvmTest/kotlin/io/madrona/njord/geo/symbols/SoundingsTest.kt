package io.madrona.njord.geo.symbols

import kotlin.test.*

class SoundingsTest {

    @Test
    fun displayDepths() {
        val meters = 0.3
        val depths = displayDepths(meters)
        assertEquals(0, depths.fathoms)
        assertEquals(0, depths.fathomsFeet)
        assertEquals(1, depths.feet)
        assertEquals(0, depths.metersWhole)
        assertEquals(3, depths.metersTenths)
    }
}