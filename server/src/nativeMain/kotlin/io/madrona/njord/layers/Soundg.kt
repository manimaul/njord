package io.madrona.njord.layers

import addSoundingConversions
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.doubleValue
import io.madrona.njord.model.*
import kotlinx.serialization.json.JsonElement

/**
 * Sounding SOUNDG
 * Geometric primitives: Point
 *
 * Set Attribute_A: 	EXPSOU; NOBJNM; OBJNAM; QUASOU; (?)SOUACC; STATUS; (?)TECSOU; VERDAT;
 * Set Attribute_B: 	INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C: 	RECDAT; RECIND; SORDAT; SORIND;
 *
 * Definition:
 * A measured water depth or spot which has been reduced to a vertical datum (may be a drying height).
 * References
 * INT 1:	II 10-15;
 * S-4:	403.1; 410; 412-412.4; 413.1; 417.3;
 * Remarks:
 * The value of the sounding is encoded in the 3-D Coordinate field of the Spatial Record Structure (see S-57 Part 3).
 * Drying heights (drying soundings) are indicated by a negative value.
 * Distinction:
 * depth area; wreck; underwater/awash rock; obstruction;
 *
 * Drying heights (S-52 PresLib SNDFRM04): the depth parts in the tile are negative, so every text
 * field renders `abs` of its part - the digits are drawn positive, never with a minus sign - the
 * shallow colour applies (a negative value is always under the safety depth) and the layers that
 * draw the whole number carry the SOUNDSA1 underline, fitted to the digits with icon-text-fit.
 */
open class Soundg(
    private val config: ChartsConfig = Singletons.config
) : Layerable() {

    private val textSize = 16f
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.props.doubleValue("METERS")?.let { meters ->
            feature.props.addSoundingConversions(meters)
        }
    }

    override fun layers(options: LayerableOptions) = when (options.depth) {
        Depth.FATHOMS -> fathoms(theme = options.theme)
        Depth.METERS -> meters(theme = options.theme)
        Depth.FEET -> feet(theme = options.theme)
    }

    private fun fathoms(theme: Theme) = sequenceOf(
        singleText(theme = theme, id = "${key}_fathoms", textKey = "FATHOMS", subTextKey = "FATHOMS_FT"),
        primaryTextLayer(theme = theme, id = "${key}_fathoms_offset", textKey = "FATHOMS", subTextKey = "FATHOMS_FT"),
        subTextLayer(theme = theme, id = "${key}_fathoms_feet_offset", textKey = "FATHOMS_FT"),
    )

    private fun feet(theme: Theme) = sequenceOf(
        singleText(theme = theme, id = "${key}_feet", textKey = "FEET"),
    )

    private fun meters(theme: Theme) = sequenceOf(
        singleText(theme = theme, id = "${key}_meters", textKey = "METERS_W", subTextKey = "METERS_T"),
        primaryTextLayer(theme = theme, id = "${key}_meters_offset", textKey = "METERS_W", subTextKey = "METERS_T"),
        subTextLayer(theme = theme, id = "${key}_meters_tenths_offset", textKey = "METERS_T"),
    )

    /** Ascending METERS gives shallower (more safety-critical) soundings priority when labels collide. */
    private val sortKeyByDepth = listOf("get", "METERS").json

    /** The magnitude of a depth part - drying heights are stored negative and drawn without a sign. */
    private fun absText(key: String): JsonElement = listOf("to-string", listOf("abs", listOf("get", key))).json

    /**
     * S-52 SOUNDSA1, the drying-height underline, only for negative soundings. An empty image name
     * resolves to no icon. The sprite is stretchable (see sprites/create_sheet.py) and fitted to the
     * text box with [dryingUnderlineFit]; the bottom padding keeps the line clear of the text halo.
     *
     * The icon takes no part in collision detection ([Layout.iconAllowOverlap] and
     * [Layout.iconIgnorePlacement]): its fitted box reaches into the subscript digit's box, which
     * is placed first, and MapLibre would then drop the whole leading-digit symbol. With the icon
     * exempt, the symbol places on its text alone and the underline shows exactly when the digits do.
     */
    private val dryingUnderline: JsonElement =
        listOf("case", listOf("<", listOf("get", "METERS"), 0), Sprite.SOUNDSA1.name, "").json
    private val dryingUnderlineFit = IconTextFit.BOTH
    private val dryingUnderlinePadding = listOf(0f, 0f, 3f, 0f)

    private fun paint(theme: Theme) = Paint(
        textColor = textColor(theme),
        textHaloColor = colorFrom(Color.DEPDW, theme),
        textHaloWidth = 2.5f
    )

    private fun singleText(theme: Theme, id: String, textKey: String, subTextKey: String? = null) = Layer(
        id = id,
        type = LayerType.SYMBOL,
        sourceLayer = sourceLayer,
        filter = subTextKey?.let {
            listOf(
                Filters.all,
                Filters.eqTypePoint,
                listOf(
                    Filters.any,
                    listOf(">=", "METERS", config.deepDepth),
                    listOf("==", subTextKey, 0),
                ),
            ).json
        } ?: Filters.eqTypePoint,
        layout = Layout(
            iconImage = dryingUnderline,
            iconTextFit = dryingUnderlineFit,
            iconTextFitPadding = dryingUnderlinePadding,
            iconAllowOverlap = true,
            iconIgnorePlacement = true,
            textFont = listOf(Font.ROBOTO_BOLD),
            textAnchor = Anchor.CENTER,
            textJustify = TextJustify.CENTER,
            textField = absText(textKey),
            textAllowOverlap = false,
            textIgnorePlacement = false,
            textSize = textSize,
            symbolPlacement = Placement.POINT,
            symbolSortKey = sortKeyByDepth,
        ),
        paint = paint(theme),
    )

    /**
     * Whole-number depth, anchored so it sits up-and-left of the feature point (its own
     * bottom-right corner offset down from the point) — paired with [subTextLayer], whose
     * top-left anchor sits just right of the same point, so the smaller fractional digit tucks
     * in below and to the right of this number regardless of how many digits it has.
     */
    private fun primaryTextLayer(theme: Theme, id: String, textKey: String, subTextKey: String) = Layer(
        id = id,
        type = LayerType.SYMBOL,
        sourceLayer = sourceLayer,
        filter = listOf(
            Filters.all,
            Filters.eqTypePoint,
            listOf("!=", subTextKey, 0),
            listOf("<", "METERS", config.deepDepth),
        ).json,
        layout = Layout(
            iconImage = dryingUnderline,
            iconTextFit = dryingUnderlineFit,
            iconTextFitPadding = dryingUnderlinePadding,
            iconAllowOverlap = true,
            iconIgnorePlacement = true,
            textFont = listOf(Font.ROBOTO_BOLD),
            textAnchor = Anchor.BOTTOM_RIGHT,
            //x (neg left / pos right), y (neg up / pos down)
            textOffset = listOf(0.0f, 0.6f).json,
            textJustify = TextJustify.CENTER,
            textField = absText(textKey),
            textAllowOverlap = false,
            textIgnorePlacement = false,
            textSize = textSize,
            // Zeroed so the default 2px collision buffer doesn't bridge the intentional ~1-2px
            // gap to subTextLayer's box and flag this pair as colliding with each other.
            textPadding = 0f,
            symbolPlacement = Placement.POINT,
            symbolSortKey = sortKeyByDepth,
        ),
        paint = paint(theme),
    )

    /** The fractional digit. SOUNDSA1 underlines only the leading digits, so no icon here. */
    private fun subTextLayer(theme: Theme, id: String, textKey: String) = Layer(
        id = id,
        type = LayerType.SYMBOL,
        sourceLayer = sourceLayer,
        filter = listOf(
            Filters.all,
            Filters.eqTypePoint,
            listOf("!=", textKey, 0),
            listOf("<", "METERS", config.deepDepth),
        ).json,
        layout = Layout(
            textFont = listOf(Font.ROBOTO_BOLD),
            textAnchor = Anchor.TOP_LEFT,
            //x (neg left / pos right), y (neg up / pos down)
            textOffset = listOf(0.1f, 0.0f).json,
            textJustify = TextJustify.CENTER,
            textField = absText(textKey),
            textAllowOverlap = false,
            textIgnorePlacement = false,
            textSize = textSize - 4f,
            textPadding = 0f,
            symbolPlacement = Placement.POINT,
            symbolSortKey = sortKeyByDepth,
        ),
        paint = paint(theme),
    )

    private fun textColor(theme: Theme): JsonElement = listOf(
        "case",
        listOf("<=", listOf("get", "METERS"), config.deepDepth),
        colorFrom(Color.SNDG2, theme),
        colorFrom(Color.SNDG1, theme)
    ).json
}
