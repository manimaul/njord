package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.model.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Marks a BRIDGE whose CATBRG is an opening type, so the style knows to read VERCCL/VERCOP rather
 * than VERCLR. S-52 makes that choice through the presentation library's lookup table, which is
 * keyed on CATBRG; the style can't make it itself because CATBRG is a list attribute and GDAL
 * flattens it to the literal string `["5"]` on the way into the tile.
 */
const val OPENING_BRIDGE = "_OPBR"

fun ChartFeature.openingBridge() {
    props[OPENING_BRIDGE] = true.json
}

/**
 * Vertical and horizontal clearance labels — CHBLK on a CHWHT halo, per the S-52 presentation
 * library's `TE('clr %4.1lf','VERCLR',3,1,2,'15110',1,1,CHBLK,11)`.
 *
 * Split out of the object-class layerables (as [LndareLabel] is from [Lndare]) so the labels can
 * be registered separately, late in the draw order — clearance is text group 11 and display-base
 * in S-52, so it has to sit above the buoys, obstructions and area symbology that the bridge
 * itself is drawn among. One instance is registered per object class whose presentation library
 * lookup carries a `clr` instruction (BRIDGE, CBLOHD, CONVYR, CRANES, GATCON).
 *
 * The label text is composed by style expressions off the raw S-57 attributes, which reach the
 * tile as doubles, rather than being pre-rendered into the tile as strings. That keeps tiles
 * unit-agnostic — the same tile serves feet and metres — and keeps four strings per feature out
 * of every tile.
 *
 * Units follow the depth preference; fathoms are meaningless for a height, and fathom charts are
 * imperial anyway, so anything but metres renders feet.
 */
class ClearanceLabel(
    private val objectClass: String,
) : Layerable(customKey = "${objectClass}_CLEARANCE") {

    override val sourceLayer: String = objectClass

    /**
     * Deliberately unfiltered by geometry type and placed as a point, so a bridge encoded as a
     * polygon, a line or a point all get one upright label at the geometry's centre. Line
     * placement would rotate the text to follow the span, which is much harder to read and is not
     * what the presentation library's screen-aligned `TE()` does.
     *
     * The two labels anchor away from each other vertically rather than using fixed offsets, so
     * an opening bridge's two-line `clr cl` / `clr op` label grows upward without running into
     * `hor clr` below it. The x offset mirrors the presentation library's `XOFFS=1` — one text
     * body right of the symbol.
     */
    override fun layers(options: LayerableOptions): Sequence<Layer> = sequenceOf(
        label(
            id = "${key}_vertical",
            text = verticalText(options.depth),
            theme = options.theme,
            textAnchor = Anchor.BOTTOM_LEFT,
            textOffset = Offset.Coord(x = 1.2f, y = -0.3f),
        ),
        label(
            id = "${key}_horizontal",
            text = horizontalText(options.depth),
            theme = options.theme,
            textAnchor = Anchor.TOP_LEFT,
            textOffset = Offset.Coord(x = 1.2f, y = 0.3f),
        ),
    )

    private fun label(
        id: String,
        text: JsonElement,
        theme: Theme,
        textAnchor: Anchor,
        textOffset: Offset,
    ) = Layer(
        id = id,
        type = LayerType.SYMBOL,
        sourceLayer = sourceLayer,
        layout = Layout(
            textFont = listOf(Font.ROBOTO_BOLD),
            textAnchor = textAnchor,
            textJustify = TextJustify.LEFT,
            textField = text,
            textOffset = textOffset.property,
            textSize = 14f,
            // Zeroed so the default 2px collision buffer doesn't bridge the intentional gap
            // between the vertical and horizontal labels and drop one of the pair.
            textPadding = 0f,
            symbolPlacement = Placement.POINT,
        ),
        paint = Paint(
            textColor = colorFrom(Color.CHBLK, theme).json,
            textHaloColor = colorFrom(Color.CHWHT, theme),
            textHaloWidth = 2.5f
        )
    )

    /**
     * An opening bridge carries a closed and/or an open clearance; everything else carries a
     * single VERCLR. An opening bridge encoding neither (which happens in real NOAA data) falls
     * through to the VERCLR branch.
     */
    private fun verticalText(depth: Depth): JsonElement = listOf(
        "case",
        listOf("all", opening, present("VERCCL"), present("VERCOP")),
        listOf("concat", "clr cl ", clearance("VERCCL", depth), "\n", "clr op ", clearance("VERCOP", depth)),
        listOf("all", opening, present("VERCCL")),
        listOf("concat", "clr cl ", clearance("VERCCL", depth)),
        listOf("all", opening, present("VERCOP")),
        listOf("concat", "clr op ", clearance("VERCOP", depth)),
        present("VERCLR"),
        listOf("concat", "clr ", clearance("VERCLR", depth)),
        "",
    ).json

    /** Not rendered by S-52 at all — horizontal clearance is a njord addition. */
    private fun horizontalText(depth: Depth): JsonElement = listOf(
        "case",
        present("HORCLR"),
        listOf("concat", "hor clr ", clearance("HORCLR", depth)),
        "",
    ).json

    private val opening = listOf("has", OPENING_BRIDGE)

    /**
     * `has` alone isn't enough — [io.madrona.njord.geo.TileEncoder] only drops a "0" for
     * enumerated attributes, and these are floats, so a zero clearance would reach the tile and
     * render as `clr 0.0m`. `all` short-circuits, so `>` never sees a missing property.
     */
    private fun present(key: String) = listOf("all", listOf("has", key), listOf(">", listOf("get", key), 0))

    /**
     * Metres keep S-52's `%4.1lf` — one fraction digit, so 17 reads as `17.0`, which needs
     * `number-format`'s min-fraction-digits. Feet are whole and use `round`/`to-string` instead,
     * specifically to dodge `number-format`'s digit grouping: horizontal clearances routinely run
     * past 1000 ft (the Tacoma Narrows is 2737) and `hor clr 2,737ft` is not wanted.
     *
     * That leaves one asymmetry: a horizontal clearance of 1000 m or more would group, as
     * `hor clr 1,234.5m`. There is no way to turn grouping off in the style spec, and no span in
     * NOAA's data comes close, so it is left rather than hand-rolling decimal assembly out of
     * floor/%/concat.
     */
    private fun clearance(key: String, depth: Depth): List<Any> = when (depth) {
        Depth.METERS -> listOf(
            "concat",
            listOf("number-format", listOf("get", key), oneFractionDigit),
            "m",
        )

        Depth.FEET, Depth.FATHOMS -> listOf(
            "concat",
            listOf("to-string", listOf("round", listOf("*", listOf("get", key), FEET_PER_METER))),
            "ft",
        )
    }

    companion object {
        private const val FEET_PER_METER = 3.28084

        /** Locale pinned so the decimal separator doesn't follow the viewer's browser locale. */
        private val oneFractionDigit = JsonObject(
            mapOf(
                "locale" to JsonPrimitive("en-US"),
                "min-fraction-digits" to JsonPrimitive(1),
                "max-fraction-digits" to JsonPrimitive(1),
            )
        )
    }
}
