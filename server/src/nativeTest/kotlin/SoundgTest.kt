import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.layers.Obstrn
import io.madrona.njord.layers.Soundg
import io.madrona.njord.model.Depth
import io.madrona.njord.model.IconTextFit
import io.madrona.njord.model.Layer
import io.madrona.njord.model.Sprite
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.resources
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drying heights per S-52 PresLib SNDFRM04: the depth parts in the tile are negative (S-57
 * convention, see `displayDepths`), and the style must draw them as positive digits in the shallow
 * colour with the SOUNDSA1 underline. Before this, the `> 0` tenths filters dropped every drying
 * height with a non-zero tenth from the map entirely.
 */
class SoundgTest {

    @BeforeTest
    fun setup() {
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
    }

    private fun layers(depth: Depth): List<Layer> =
        Soundg().layers(LayerableOptions(depth, ThemeMode.Day)).toList()

    private fun Layer.text() = layout?.textField.toString()

    private val subscriptIds = setOf("SOUNDG_meters_tenths_offset", "SOUNDG_fathoms_feet_offset")

    @Test
    fun `every text field renders the magnitude so a drying height has no minus sign`() {
        Depth.entries.forEach { depth ->
            layers(depth).forEach { layer ->
                val text = layer.text()
                assertTrue(text.startsWith("""["to-string",["abs",["get","""), "${layer.id}: $text")
            }
        }
    }

    @Test
    fun `subscript filters test for a non-zero tenth rather than a positive one`() {
        Depth.entries.flatMap { layers(it) }.forEach { layer ->
            val filter = layer.filter.toString()
            assertTrue(!filter.contains("""[">","METERS_T""""), "${layer.id}: $filter")
            assertTrue(!filter.contains("""[">","FATHOMS_FT""""), "${layer.id}: $filter")
        }
    }

    @Test
    fun `leading digits carry the drying underline fitted to the text and the subscript does not`() {
        Depth.entries.flatMap { layers(it) }.forEach { layer ->
            val layout = layer.layout ?: error("no layout on ${layer.id}")
            if (layer.id in subscriptIds) {
                assertNull(layout.iconImage, layer.id)
                assertNull(layout.iconTextFit, layer.id)
            } else {
                assertEquals(
                    """["case",["<",["get","METERS"],0],"SOUNDSA1",""]""",
                    layout.iconImage.toString(),
                    layer.id,
                )
                assertEquals(IconTextFit.BOTH, layout.iconTextFit, layer.id)
                assertEquals(4, layout.iconTextFitPadding?.size, layer.id)
                // the icon is exempt from collision so its fitted box cannot get the leading
                // digits dropped against the subscript; the symbol then places on its text alone
                assertEquals(true, layout.iconAllowOverlap, layer.id)
                assertEquals(true, layout.iconIgnorePlacement, layer.id)
            }
        }
        assertTrue(Sprite.entries.contains(Sprite.SOUNDSA1))
    }

    @Test
    fun `drying heights take the shallow colour`() {
        // SNDG2 for METERS <= deep, which every negative value satisfies
        layers(Depth.METERS).forEach { layer ->
            val color = layer.paint?.textColor.toString()
            assertTrue(color.startsWith("""["case",["<=",["get","METERS"],"""), "${layer.id}: $color")
        }
    }

    @Test
    fun `drying heights sort ahead of depths`() {
        Depth.entries.flatMap { layers(it) }.forEach { layer ->
            assertEquals("""["get","METERS"]""", layer.layout?.symbolSortKey.toString(), layer.id)
        }
    }

    @Test
    fun `the style serialises the icon fit with MapLibre's property names`() {
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val encoded = json.encodeToString(Layer.serializer(), layers(Depth.METERS).first())
        assertTrue(encoded.contains(""""icon-text-fit":"both""""), encoded)
        assertTrue(encoded.contains(""""icon-text-fit-padding":[0.0,0.0,3.0,0.0]"""), encoded)
        assertTrue(encoded.contains(""""icon-image":["case""""), encoded)
    }

    @Test
    fun `OBSTRN depth labels are the same layers`() {
        val obstrn = Obstrn().layers(LayerableOptions(Depth.METERS, ThemeMode.Day)).toList()
        val soundg = layers(Depth.METERS)
        soundg.forEach { s ->
            val o = obstrn.first { it.id == s.id.replace("SOUNDG", "OBSTRN") }
            assertEquals(s.layout?.textField, o.layout?.textField, s.id)
            assertEquals(s.layout?.iconImage, o.layout?.iconImage, s.id)
            assertEquals(s.filter, o.filter, s.id)
        }
    }

    // ---- which layers accept a given sounding --------------------------------------------------

    /** Metres-mode parts as `displayDepths` produces them. */
    private fun metres(meters: Double, whole: Int, tenths: Int) = mapOf(
        "METERS" to meters, "METERS_W" to whole.toDouble(), "METERS_T" to tenths.toDouble(),
    )

    private fun accepting(depth: Depth, props: Map<String, Double>): Set<String> =
        layers(depth).filter { it.filter?.let { f -> eval(f, props) } ?: true }.map { it.id }.toSet()

    @Test
    fun `a drying height with a tenth is drawn as whole number plus subscript`() {
        // -1.5 m: previously no layer accepted it, so it was invisible.
        assertEquals(
            setOf("SOUNDG_meters_offset", "SOUNDG_meters_tenths_offset"),
            accepting(Depth.METERS, metres(-1.5, -1, -5)),
        )
        // -0.3 m: an underlined "0" with a "3" subscript
        assertEquals(
            setOf("SOUNDG_meters_offset", "SOUNDG_meters_tenths_offset"),
            accepting(Depth.METERS, metres(-0.3, 0, -3)),
        )
    }

    @Test
    fun `a whole drying height is drawn as a single number`() {
        assertEquals(setOf("SOUNDG_meters"), accepting(Depth.METERS, metres(-2.0, -2, 0)))
    }

    @Test
    fun `depths keep their existing layer selection`() {
        assertEquals(setOf("SOUNDG_meters"), accepting(Depth.METERS, metres(0.0, 0, 0)))
        assertEquals(
            setOf("SOUNDG_meters_offset", "SOUNDG_meters_tenths_offset"),
            accepting(Depth.METERS, metres(2.3, 2, 3)),
        )
        assertEquals(setOf("SOUNDG_meters"), accepting(Depth.METERS, metres(10.0, 10, 0)))
        // at and beyond the deep threshold only the whole number is shown, tenths or not
        assertEquals(setOf("SOUNDG_meters"), accepting(Depth.METERS, metres(31.4, 31, 4)))
    }

    @Test
    fun `fathoms and feet accept drying heights too`() {
        // -1.5 m = 0 fathoms 4 feet
        assertEquals(
            setOf("SOUNDG_fathoms_offset", "SOUNDG_fathoms_feet_offset"),
            accepting(Depth.FATHOMS, mapOf("METERS" to -1.5, "FATHOMS" to 0.0, "FATHOMS_FT" to -4.0)),
        )
        assertEquals(setOf("SOUNDG_feet"), accepting(Depth.FEET, mapOf("METERS" to -1.5, "FEET" to -5.0)))
    }

    /**
     * Just enough of MapLibre's legacy filter syntax to evaluate the SOUNDG filters:
     * `["all"|"any", ...]`, `[op, key, value]` with `==`, `!=`, `<`, `<=`, `>`, `>=`, and the
     * `$type` geometry test (every input here is a point).
     */
    private fun eval(filter: JsonElement, props: Map<String, Double>): Boolean {
        val f = filter.jsonArray
        val op = f[0].jsonPrimitive.content
        return when (op) {
            "all" -> f.drop(1).all { eval(it, props) }
            "any" -> f.drop(1).any { eval(it, props) }
            else -> {
                val key = f[1].jsonPrimitive.content
                if (key == "\$type") return f[2].jsonPrimitive.content == "Point"
                val value = f[2].jsonPrimitive.double
                val actual = props[key] ?: return false
                when (op) {
                    "==" -> actual == value
                    "!=" -> actual != value
                    "<" -> actual < value
                    "<=" -> actual <= value
                    ">" -> actual > value
                    ">=" -> actual >= value
                    else -> error("unsupported filter op $op")
                }
            }
        }
    }
}
