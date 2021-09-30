package io.madrona.njord.ext

import io.madrona.njord.util.ZFinder
import kotlin.test.*

internal class ZFinderTest {

    @Test
    fun test1() {
        val zFinder = ZFinder(28)

        val scale = 17999
        val zoom = zFinder.findZoom(scale)
        assertEquals(13, zoom)
        assertEquals(28, zFinder.findZoom(1))
    }
}