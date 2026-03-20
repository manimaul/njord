import io.madrona.njord.model.Sprite
import io.madrona.njord.resources
import io.madrona.njord.util.SpriteSheet
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SpriteSheetTest {

    @BeforeTest
    fun before() {
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
    }

    @Test
    fun testSprites() {
        val spriteSheet = SpriteSheet()
        spriteSheet.spritesByTheme.forEach {
            val diff = (it.value.keys - Sprite.entries.toSet()) union (Sprite.entries.toSet() - it.value.keys)
            assertTrue(diff.isEmpty(), "expected diff $diff to be empty for theme: ${it.key}")
        }
    }
}