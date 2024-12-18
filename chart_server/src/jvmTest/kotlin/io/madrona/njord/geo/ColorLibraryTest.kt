package io.madrona.njord.geo
import io.madrona.njord.Singletons
import io.madrona.njord.model.Color
import io.madrona.njord.model.CustomTheme
import io.madrona.njord.model.ThemeMode
import kotlin.test.*

class ColorLibraryTest {

    @Test
    fun testColors() {
        val colors = Singletons.colorLibrary.colorMap
        val colorKeys = Color.entries.toSet()
        ThemeMode.entries.forEach {
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