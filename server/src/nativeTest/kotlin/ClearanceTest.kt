import File
import io.madrona.njord.layers.ClearanceLabel
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.layers.OPENING_BRIDGE
import io.madrona.njord.model.Anchor
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Layer
import io.madrona.njord.model.Placement
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.resources
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The clearance label text is composed by MapLibre style expressions rather than pre-rendered into
 * the tile, so what is worth pinning here is the `case` the style ships — which attribute each
 * branch reads, in what priority, and what it prefixes. The strings the expression actually
 * produces are exercised against a real chart in the browser.
 */
class ClearanceTest {

    @BeforeTest
    fun setup() {
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
    }

    private fun layers(depth: Depth): List<Layer> =
        ClearanceLabel("BRIDGE").layers(LayerableOptions(depth, ThemeMode.Day)).toList()

    private fun textField(depth: Depth, id: String): JsonElement =
        layers(depth).first { it.id == id }.layout?.textField ?: error("no text-field on $id")

    /** Splits a `["case", cond, out, cond, out, ..., fallback]` into its branches and fallback. */
    private fun branches(depth: Depth, id: String): Pair<List<Branch>, String> {
        val expr = textField(depth, id).jsonArray
        assertEquals("case", expr[0].jsonPrimitive.content)
        val pairs = (1 until expr.size - 1 step 2).map { Branch(expr[it], expr[it + 1]) }
        return pairs to expr.last().jsonPrimitive.content
    }

    private class Branch(val condition: JsonElement, val output: JsonElement) {
        /** `["concat", "clr cl ", <formatted value>]` */
        val prefix: String get() = output.jsonArray[1].jsonPrimitive.content
        fun reads(attribute: String) = condition.toString().contains("\"$attribute\"")
        fun gatedOnOpeningBridge() = condition.toString().contains("\"$OPENING_BRIDGE\"")
        fun isTwoLine() = output.toString().contains("\\n")
    }

    @Test
    fun `emits one vertical and one horizontal label per object class`() {
        assertEquals(
            listOf("BRIDGE_CLEARANCE_vertical", "BRIDGE_CLEARANCE_horizontal"),
            layers(Depth.FEET).map { it.id },
        )
    }

    @Test
    fun `labels are point placed and anchored apart so the pair does not collide`() {
        val (vertical, horizontal) = layers(Depth.FEET)
        assertEquals(Anchor.BOTTOM_LEFT, vertical.layout?.textAnchor)
        assertEquals(Anchor.TOP_LEFT, horizontal.layout?.textAnchor)
        listOf(vertical, horizontal).forEach {
            assertEquals(Placement.POINT, it.layout?.symbolPlacement)
            assertEquals(0f, it.layout?.textPadding)
            // unfiltered, so Point / LineString / Polygon features all get a label
            assertEquals(null, it.filter)
        }
    }

    @Test
    fun `vertical clearance prefers the opening bridge pair and falls back to VERCLR`() {
        val (branches, fallback) = branches(Depth.METERS, "BRIDGE_CLEARANCE_vertical")

        assertEquals(listOf("clr cl ", "clr cl ", "clr op ", "clr "), branches.map { it.prefix })
        assertTrue(branches[0].reads("VERCCL") && branches[0].reads("VERCOP"))
        assertTrue(branches[1].reads("VERCCL"))
        assertTrue(branches[2].reads("VERCOP"))
        assertTrue(branches[3].reads("VERCLR"))

        // only the closed-and-open branch is two lines
        assertEquals(listOf(true, false, false, false), branches.map { it.isTwoLine() })

        // VERCCL/VERCOP are only read for an opening bridge - S-52 selects those lookups by
        // CATBRG - but an opening bridge encoding neither still falls through to VERCLR
        assertEquals(listOf(true, true, true, false), branches.map { it.gatedOnOpeningBridge() })

        assertEquals("", fallback)
    }

    @Test
    fun `horizontal clearance reads HORCLR and renders nothing when absent`() {
        val (branches, fallback) = branches(Depth.FEET, "BRIDGE_CLEARANCE_horizontal")
        assertEquals(1, branches.size)
        assertEquals("hor clr ", branches[0].prefix)
        assertTrue(branches[0].reads("HORCLR"))
        assertEquals("", fallback)
    }

    @Test
    fun `a zero clearance is treated as absent`() {
        // TileEncoder only drops a "0" for enumerated attributes, and these are floats
        branches(Depth.METERS, "BRIDGE_CLEARANCE_vertical").first.forEach {
            assertTrue(it.condition.toString().contains("\">\""), it.condition.toString())
        }
    }

    @Test
    fun `metres format to one fraction digit and feet round to whole`() {
        val metres = textField(Depth.METERS, "BRIDGE_CLEARANCE_horizontal").toString()
        assertTrue(metres.contains(""""min-fraction-digits":1,"max-fraction-digits":1"""), metres)
        assertTrue(metres.contains(""""m""""), metres)

        val feet = textField(Depth.FEET, "BRIDGE_CLEARANCE_horizontal").toString()
        assertTrue(feet.contains("""["round",["*",["get","HORCLR"],3.28084]]"""), feet)
        assertTrue(feet.contains(""""ft""""), feet)
        // to-string rather than number-format: no digit grouping on spans past 1000 ft
        assertTrue(!feet.contains("number-format"), feet)
    }

    @Test
    fun `fathoms render as feet since fathoms are meaningless for a height`() {
        assertEquals(
            textField(Depth.FEET, "BRIDGE_CLEARANCE_vertical"),
            textField(Depth.FATHOMS, "BRIDGE_CLEARANCE_vertical"),
        )
    }

    @Test
    fun `each object class reads its own source layer`() {
        listOf("BRIDGE", "CBLOHD", "CONVYR", "CRANES", "GATCON").forEach { objectClass ->
            ClearanceLabel(objectClass).layers(LayerableOptions(Depth.FEET, ThemeMode.Day)).forEach {
                assertEquals(objectClass, it.sourceLayer)
                assertTrue(it.id.startsWith("${objectClass}_CLEARANCE"), it.id)
            }
        }
    }
}
