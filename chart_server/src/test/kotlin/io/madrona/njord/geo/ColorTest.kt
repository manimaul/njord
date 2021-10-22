package io.madrona.njord.geo
import io.madrona.njord.geo.symbols.Color
import kotlin.test.*

class ColorTest {

    @Test
    fun testGreen() {
        assertEquals(listOf(Color.Green), Color.fromProp(mutableMapOf("COLOUR" to listOf("4"))))
        assertEquals(listOf(Color.Green), Color.fromProp(mutableMapOf("COLOUR" to listOf(4))))
    }
}