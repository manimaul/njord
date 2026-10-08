import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.layers.NameLabel
import io.madrona.njord.layers.formatBearing
import io.madrona.njord.model.Anchor
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Layer
import io.madrona.njord.model.Placement
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.resources
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * S-52 text group 11 bearings (`TE('%03.0lf deg','ORIENT',...)`) and text group 21 names for
 * position reporting (`TE('by %s','OBJNAM',...)` etc).
 */
class TextGroupLabelTest {

    @BeforeTest
    fun setup() {
        resources = File("./src/nativeMain/resources").getAbsolutePath().toString()
    }

    @Test
    fun `bearings are whole degrees zero padded to three digits`() {
        assertEquals("005 deg", formatBearing(5.0))
        assertEquals("045 deg", formatBearing(45.4))
        assertEquals("046 deg", formatBearing(45.5))
        assertEquals("270 deg", formatBearing(270.0))
        assertEquals("000 deg", formatBearing(0.0))
    }

    private fun nameLayer(objectClass: String): Layer =
        NameLabel.all().first { it.sourceLayer == objectClass }
            .layers(LayerableOptions(Depth.METERS, ThemeMode.Day)).single()

    private fun text(objectClass: String) = nameLayer(objectClass).layout?.textField.toString()

    @Test
    fun `presentation library prefixes`() {
        listOf("BOYCAR", "BOYINB", "BOYISD", "BOYLAT", "BOYSAW", "BOYSPP", "LITFLT").forEach {
            assertEquals("""["case",["has","OBJNAM"],["concat","by ",["to-string",["get","OBJNAM"]]],""]""", text(it))
        }
        listOf("BCNCAR", "BCNISD", "BCNLAT", "BCNSAW", "BCNSPP", "DAYMAR").forEach {
            assertEquals("""["case",["has","OBJNAM"],["concat","bn ",["to-string",["get","OBJNAM"]]],""]""", text(it))
        }
        assertEquals("""["case",["has","OBJNAM"],["concat","LtV ",["to-string",["get","OBJNAM"]]],""]""", text("LITVES"))
        assertEquals("""["case",["has","OBJNAM"],["concat","Prod ",["to-string",["get","OBJNAM"]]],""]""", text("OFSPLF"))
        assertEquals("""["case",["has","OBJNAM"],["concat","Plt ",["to-string",["get","OBJNAM"]]],""]""", text("PILBOP"))
        assertEquals("""["get","OBJNAM"]""", text("BRIDGE"))
        assertEquals("""["get","INFORM"]""", text("DISMAR"))
    }

    @Test
    fun `marks are labelled above left of the symbol`() {
        val layer = nameLayer("BOYLAT")
        assertEquals(Anchor.BOTTOM_RIGHT, layer.layout?.textAnchor)
        assertEquals("[-1.2,-1.2]", layer.layout?.textOffset.toString())
    }

    @Test
    fun `labels are unfiltered and point placed with unique ids`() {
        val all = NameLabel.all().flatMap { it.layers(LayerableOptions(Depth.METERS, ThemeMode.Day)) }
        all.forEach {
            assertNull(it.filter)
            assertEquals(Placement.POINT, it.layout?.symbolPlacement)
            assertEquals("${it.sourceLayer}_NAME", it.id)
        }
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }
}
