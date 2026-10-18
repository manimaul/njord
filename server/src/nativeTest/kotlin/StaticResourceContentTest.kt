import io.ktor.http.*
import io.madrona.njord.endpoints.contentType
import io.madrona.njord.endpoints.resourceName
import io.madrona.njord.resources
import io.madrona.njord.util.resourceBytes
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StaticResourceContentTest {

    @BeforeTest
    fun before() {
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
    }

    @Test
    fun fontStackWithSpaceIsNotReEncoded() {
        val segments = listOf("fonts", "Roboto Bold", "0-255.pbf")
        assertEquals("www/fonts/Roboto Bold/0-255.pbf", segments.resourceName())
        assertEquals(ContentType.parse("application/x-protobuf"), segments.contentType())
    }

    @Test
    fun everyStyledFontStackServesGlyphs() {
        listOf("Roboto Bold", "Roboto Regular", "Roboto Medium", "Roboto Italic").forEach { stack ->
            val name = assertNotNull(listOf("fonts", stack, "0-255.pbf").resourceName())
            assertNotNull(resourceBytes(name), "expected glyphs at $name")
        }
    }

    @Test
    fun spritesAreServed() {
        assertNotNull(resourceBytes(assertNotNull(listOf("sprites", "day_simplified.json").resourceName())))
    }

    @Test
    fun missingResourceIsNullNotAnError() {
        assertNull(resourceBytes(assertNotNull(listOf("sprites", "nonexistent.json").resourceName())))
        assertNull(resourceBytes(assertNotNull(listOf("fonts", "Nope Bold", "0-255.pbf").resourceName())))
    }

    @Test
    fun traversalIsRefused() {
        // Inside the resources directory, so the realpath guard alone would serve it.
        assertNull(listOf("..", "config", "application.json").resourceName())
        assertNull(listOf("sprites", ".", "day_simplified.json").resourceName())
        assertNull(listOf("sprites", "", "day_simplified.json").resourceName())
        assertNull(emptyList<String>().resourceName())
        // Outside it: the realpath guard still holds for callers that bypass resourceName.
        assertNull(resourceBytes("www/../../build.gradle.kts"))
    }

    @Test
    fun unknownExtensionHasNoContentType() {
        assertNull(listOf("fonts", "Roboto Bold", "0-255.exe").contentType())
        assertNull(emptyList<String>().contentType())
    }
}
