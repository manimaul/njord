package io.madrona.njord.ext

import com.fasterxml.jackson.annotation.JsonProperty
import io.madrona.njord.layers.Theme
import io.madrona.njord.model.Depth
import kotlin.test.*

internal class EnumExtTest {

    @Test
    fun test1() {
        val value1 = fromString<Depth>("meters")
        assertEquals(Depth.METERS, value1)
        val value2 = fromString<Depth>("mEteRs")
        assertEquals(Depth.METERS, value2)
        val value3 = fromString<Depth>("METERS")
        assertEquals(Depth.METERS, value3)
        val value4 = fromString<Depth>("foo")
        assertNull(value4)
    }

    @Test
    fun testLetFromStrings() {
        letFromStrings("meters", "day") { depths: Depth, theme: Theme ->
            assertEquals(Depth.METERS, depths)
            assertEquals(Theme.Day, theme)
        } ?: fail()

        val passed = letFromStrings<Depth, Theme, Boolean>("meters", "foo") { _, _ ->
            false
        } ?: true
        assertTrue(passed)
    }
}