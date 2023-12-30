package io.madrona.njord.geo
import io.madrona.njord.Singletons
import io.madrona.njord.layers.Color
import io.madrona.njord.layers.CustomTheme
import io.madrona.njord.layers.ThemeMode
import kotlin.test.*

class ColorLibraryTest {

    @Test
    fun testColors() {
        val colors = Singletons.colorLibrary.colorMap
        val colorKeys = Color.values().toSet()
        ThemeMode.values().forEach {
            val themeColors = colors.library[it]?.keys
            assertNotNull(themeColors)
            assertEquals(colorKeys, themeColors)
        }
    }

    @Test
    fun testCustomColor() {
        val lib = Singletons.colorLibrary
        assertEquals("#ffe0b7",  lib.colorFrom(Color.LANDA, CustomTheme(ThemeMode.Day, "shom")))
        assertEquals("#BFBE8F",  lib.colorFrom(Color.LANDA, CustomTheme(ThemeMode.Day, "foo")))
        assertEquals("#BFBE8F",  lib.colorFrom(Color.LANDA, ThemeMode.Day))
    }
}