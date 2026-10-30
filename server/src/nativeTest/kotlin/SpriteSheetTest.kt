import io.madrona.njord.model.Sprite
import io.madrona.njord.util.SpriteSheet
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpriteSheetTest {

    @BeforeTest
    fun before() {
        TestEnv.init()
    }

    @Test
    fun testSprites() {
        val spriteSheet = SpriteSheet()
        spriteSheet.spritesByTheme.forEach {
            val diff = (it.value.keys - Sprite.entries.toSet()) union (Sprite.entries.toSet() - it.value.keys)
            assertTrue(diff.isEmpty(), "expected diff $diff to be empty for theme: ${it.key}")
        }
    }

    @Test
    fun `the drying underline is stretchable in every theme`() {
        // SOUNDSA1 is fitted to the sounding text with icon-text-fit, which needs the stretch
        // metadata create_sheet.py adds for it; the line rides outside the content box.
        SpriteSheet().spritesByTheme.forEach { (theme, sprites) ->
            val info = sprites[Sprite.SOUNDSA1] ?: error("no SOUNDSA1 in $theme")
            assertNotNull(info.stretchX, "$theme stretchX")
            assertNotNull(info.stretchY, "$theme stretchY")
            val content = assertNotNull(info.content, "$theme content")
            assertTrue(content[3] < info.height, "$theme: content ${content} must exclude the underline rows")
        }
    }
}