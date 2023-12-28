package io.madrona.njord.geo
import io.madrona.njord.Singletons
import io.madrona.njord.layers.Color
import io.madrona.njord.layers.Theme
import kotlin.test.*

class ColourTest {

    @Test
    fun testColors() {
        val colors = Singletons.colorLibrary.colorMap
        val colorKeys = Color.values().toSet()
        Theme.values().forEach {
            val themeColors = colors.library[it]?.keys
            assertNotNull(themeColors)
            assertEquals(colorKeys, themeColors)
        }
    }
}