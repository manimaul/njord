import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.layers.Lndelv
import io.madrona.njord.layers.Sbdare
import io.madrona.njord.layers.set.StandardLayers
import io.madrona.njord.model.Depth
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.resources
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SBDARE, MAGVAR, LNDELV, PIPOHD, RADSTA and TIDEWY - previously commented out of [StandardLayers].
 */
class MissingLayersTest {

    @BeforeTest
    fun setup() {
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
    }

    @Test
    fun `nature of seabed uses the PresLib abbreviations space separated`() {
        assertEquals("M S G", Sbdare.natsur(listOf(1, 4, 6)))
        assertEquals("S Sh", Sbdare.natsur(listOf(4, 17)))
        // rock, lava and boulder all abbreviate to R
        assertEquals("R", Sbdare.natsur(listOf(9, 11, 18)))
        // values outside the PresLib table (10 isn't a NATSUR value) are dropped
        assertNull(Sbdare.natsur(listOf(10)))
        assertNull(Sbdare.natsur(emptyList()))
    }

    @Test
    fun `land elevation follows the depth preference in whole units`() {
        val metres = Lndelv().layers(LayerableOptions(Depth.METERS, ThemeMode.Day))
            .mapNotNull { it.layout?.textField }.single().toString()
        assertEquals("""["concat",["to-string",["round",["get","ELEVAT"]]],"m"]""", metres)

        val feet = Lndelv().layers(LayerableOptions(Depth.FEET, ThemeMode.Day))
            .mapNotNull { it.layout?.textField }.single().toString()
        assertTrue(feet.contains("3.28084") && feet.contains("\"ft\""), feet)
    }

    @Test
    fun `standard layers all build with unique ids`() {
        val ids = StandardLayers().layers
            .flatMap { it.layers(LayerableOptions(Depth.METERS, ThemeMode.Day)) }
            .map { it.id }
            .toList()
        assertEquals(ids.size, ids.toSet().size, ids.groupBy { it }.filter { it.value.size > 1 }.keys.toString())
        // removed MAGVAR
        listOf("SBDARE", "LNDELV", "PIPOHD", "RADSTA", "TIDEWY", "PIPOHD_CLEARANCE").forEach { key ->
            assertTrue(ids.any { it.startsWith(key) }, "no layers for $key")
        }
    }
}
