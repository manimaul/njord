package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.model.*
import kotlinx.serialization.json.JsonElement

/**
 * S-52 text group 21, "names for position reporting" - `TE('by %s','OBJNAM',...)` on buoys,
 * `TE('bn %s','OBJNAM',...)` on beacons and daymarks, `LtV`, `Prod` and `Plt` on light vessels,
 * offshore platforms and pilot boarding places, and a plain `TX(OBJNAM,...)` / `TX(INFORM,...)` on
 * bridges and distance marks.
 *
 * Split out of the object-class layerables (as [ClearanceLabel] is) so the names can be registered
 * once, after every mark symbol, rather than threading a label layer through each buoy and beacon
 * class and its subclasses.
 *
 * Like [ClearanceLabel], the text is composed by a style expression off the raw attribute, so
 * nothing is added to the tile, and the layer is unfiltered by geometry type and point placed, so
 * an area (OFSPLF, PILBOP) or line (BRIDGE) gets one upright label at its centre.
 *
 * @param prefix the `TE` format's text before `%s`, or null for a bare `TX`.
 */
class NameLabel(
    private val objectClass: String,
    private val prefix: String?,
    private val placement: NamePlacement = NamePlacement.UpperLeft,
    private val attribute: String = "OBJNAM",
) : Layerable(customKey = "${objectClass}_NAME") {

    override val sourceLayer: String = objectClass

    override fun layers(options: LayerableOptions): Sequence<Layer> = sequenceOf(
        Layer(
            id = key,
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            layout = Layout(
                textFont = listOf(Font.ROBOTO_BOLD),
                textAnchor = placement.anchor,
                textJustify = placement.justify,
                textField = text(),
                textOffset = placement.offset.property,
                textSize = 14f,
                symbolPlacement = Placement.POINT,
            ),
            paint = Paint(
                textColor = colorFrom(Color.SNDG2, options.theme).json,
                textHaloColor = colorFrom(Color.DEPDW, options.theme),
                textHaloWidth = 2.5f
            )
        )
    )

    private fun text(): JsonElement = if (prefix == null) {
        listOf("get", attribute).json
    } else {
        listOf(
            "case",
            listOf("has", attribute),
            listOf("concat", "$prefix ", listOf("to-string", listOf("get", attribute))),
            "",
        ).json
    }

    companion object {
        /** Registered after all of the mark symbols, see `StandardLayers`. */
        fun all(): List<NameLabel> = listOf(
            "BOYCAR", "BOYINB", "BOYISD", "BOYLAT", "BOYSAW", "BOYSPP", "LITFLT",
        ).map { NameLabel(it, "by") } + listOf(
            "BCNCAR", "BCNISD", "BCNLAT", "BCNSAW", "BCNSPP", "DAYMAR",
        ).map { NameLabel(it, "bn") } + listOf(
            NameLabel("LITVES", "LtV"),
            NameLabel("OFSPLF", "Prod", NamePlacement.UpperRight),
            NameLabel("PILBOP", "Plt", NamePlacement.UpperRight),
            NameLabel("BRIDGE", null, NamePlacement.Left),
            NameLabel("DISMAR", null, NamePlacement.DistanceMark, attribute = "INFORM"),
        )
    }
}

/**
 * The presentation library's `TE`/`TX` justification and offset, where offsets are in text body
 * units (positive y is down, as in MapLibre). One body is taken as 1.2 em, as in [ClearanceLabel].
 */
enum class NamePlacement(
    val anchor: Anchor,
    val justify: TextJustify,
    val offset: Offset.Coord,
) {
    /** `HJUST=2 (right), VJUST=1 (bottom), XOFFS=-1, YOFFS=-1` - buoys, beacons, daymarks, LITFLT, LITVES. */
    UpperLeft(Anchor.BOTTOM_RIGHT, TextJustify.RIGHT, Offset.Coord(x = -1.2f, y = -1.2f)),

    /** `HJUST=3 (left), VJUST=1 (bottom), XOFFS=1, YOFFS=-1` - OFSPLF, PILBOP. */
    UpperRight(Anchor.BOTTOM_LEFT, TextJustify.LEFT, Offset.Coord(x = 1.2f, y = -1.2f)),

    /**
     * BRIDGE's `TX(OBJNAM,3,1,2,'15110',1,0,...)` is the same spot as its `clr` text, which
     * [ClearanceLabel] already occupies and grows upward from, so the name goes to the left of the
     * bridge instead.
     */
    Left(Anchor.RIGHT, TextJustify.RIGHT, Offset.Coord(x = -1.2f, y = 0f)),

    /** `HJUST=2 (right), VJUST=1 (bottom), XOFFS=2, YOFFS=0` - DISMAR. */
    DistanceMark(Anchor.BOTTOM_RIGHT, TextJustify.RIGHT, Offset.Coord(x = 2.4f, y = 0f)),
}
