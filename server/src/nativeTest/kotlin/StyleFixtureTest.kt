import io.madrona.njord.Singletons
import io.madrona.njord.endpoints.StyleHandler
import io.madrona.njord.layers.LayerFactory
import io.madrona.njord.model.CustomTheme
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Theme
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P-SY-7: the complete MapLibre style `GET /v1/style/{depth}/{theme}[?c=]` serves, for every
 * depth × theme mode × colour library (default, and the custom `sweden` / `shom` palettes), with
 * the base URL fixed. One file per combination, holding the style as parsed JSON so the diff is
 * readable; the port must serialise the same document after JSON normalisation (layer order
 * included - it is draw order).
 */
class StyleFixtureTest {

    @BeforeTest
    fun before() {
        TestEnv.init()
    }

    @Serializable
    data class StyleInput(val baseUrl: String, val depth: String, val theme: String, val custom: String? = null)

    private val baseUrl = "https://njord.test"

    private fun combos(): List<StyleInput> = Depth.entries.flatMap { depth ->
        ThemeMode.entries.flatMap { mode ->
            val d = depth.name.lowercase()
            val m = mode.name.lowercase()
            listOf(StyleInput(baseUrl, d, m)) +
                    Singletons.colorLibrary.colorMap.custom.keys.sorted().map { StyleInput(baseUrl, d, m, it) }
        }
    }

    private fun style(input: StyleInput): String {
        val depth = Depth.valueOf(input.depth.uppercase())
        val mode = ThemeMode.entries.first { it.name.equals(input.theme, ignoreCase = true) }
        val theme: Theme = input.custom?.let { CustomTheme(mode, it) } ?: mode
        // A fresh factory: Layerable numbers its layer ids with a per-instance counter that keeps
        // counting across requests, so the served ids drift (`DEPARE_fill_55`, then `_57`). The
        // fixture pins what the first request after startup serves; the port should make that stable.
        return StyleHandler(layerFactory = LayerFactory()).styleString(input.baseUrl, "${mode.name.lowercase()}_simplified", depth, theme)
    }

    @Test
    fun everyStyle() {
        val combos = combos()
        assertEquals(3 * 3 * 3, combos.size, "3 depths × 3 modes × (default + sweden + shom)")
        combos.forEach { input ->
            val name = listOfNotNull(input.depth, input.theme, input.custom).joinToString("_")
            val table = fixtures.table(
                "symbology/style/$name.json",
                FixtureHeader(
                    pins = "Full MapLibre style for depth=${input.depth} theme=${input.theme}${input.custom?.let { " c=$it" } ?: ""}: layer order, ids (as numbered by the first request after startup - Kotlin's per-layerable counter keeps counting across requests), filters, expressions, paint and layout, glyph/sprite/tile_json URLs under the fixed base URL",
                    decisions = listOf("API-2", "SY-13"),
                    generator = "StyleFixtureTest.everyStyle",
                    source = "StyleHandler.styleString with the shipped config/application.json thresholds and resources/colors.json",
                ),
                listOf(input),
            ) { Json.parseToJsonElement(style(it)) }

            val doc = table.cases.single().expected.jsonObject
            assertEquals(8, doc["version"]!!.jsonPrimitive.content.toInt(), name)
            assertEquals("$baseUrl/v1/content/fonts/{fontstack}/{range}.pbf", doc["glyphs"]!!.jsonPrimitive.content)
            assertEquals("$baseUrl/v1/content/sprites/${input.theme}_simplified", doc["sprite"]!!.jsonPrimitive.content)
            val ids = doc["layers"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
            assertEquals(ids.size, ids.distinct().size, "duplicate layer ids in $name: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}")
            assertTrue(ids.size > 200, "$name has only ${ids.size} layers")
        }
    }

    /** A custom palette changes colours only, never the layer list. */
    @Test
    fun `custom themes keep the layer list of their mode`() {
        val base = Json.parseToJsonElement(style(StyleInput(baseUrl, "meters", "day"))).jsonObject["layers"]!!.jsonArray
        Singletons.colorLibrary.colorMap.custom.keys.forEach { custom ->
            val layers = Json.parseToJsonElement(style(StyleInput(baseUrl, "meters", "day", custom))).jsonObject["layers"]!!.jsonArray
            assertEquals(
                base.map { it.jsonObject["id"]!!.jsonPrimitive.content },
                layers.map { it.jsonObject["id"]!!.jsonPrimitive.content },
                custom,
            )
        }
    }
}
