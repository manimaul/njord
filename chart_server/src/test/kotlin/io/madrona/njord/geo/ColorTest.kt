package io.madrona.njord.geo
import io.madrona.njord.Singletons
import io.madrona.njord.layers.Theme
import kotlin.test.*

class ColorTest {

    @Test
    fun testGreen() {
        val colors = Singletons.colorLibrary.colorMap
        val colorKeys = colors.library[Theme.Day]!!.keys
        assertEquals(63, colorKeys.size)
        Theme.values().forEach {
            val themeColors = colors.library[it]
            assertNotNull(themeColors)
            assertEquals(colorKeys, themeColors.keys)
        }
    }
}