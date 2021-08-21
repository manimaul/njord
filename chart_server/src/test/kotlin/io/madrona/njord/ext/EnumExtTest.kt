package io.madrona.njord.ext

import io.madrona.njord.model.Depth
import io.madrona.njord.model.StyleColor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
        letFromStrings("meters", "day") { depths: Depth, color: StyleColor ->
            assertEquals(Depth.METERS, depths)
            assertEquals(StyleColor.DAY, color)
        } ?: fail()

        val passed = letFromStrings<Depth, StyleColor, Boolean>("meters", "foo") { _, _ ->
            false
        } ?: true
        assertTrue(passed)
    }
}