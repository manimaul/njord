package io.madrona.njord.layers

import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.addSoundingConversions
import io.madrona.njord.geo.symbols.doubleValue
import io.madrona.njord.model.*

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
 */
open class Soundg(
    private val config: ChartsConfig = Singletons.config
) : Layerable() {

    override fun preTileEncode(feature: ChartFeature) {
        feature.props.doubleValue("METERS")?.let { meters ->
            feature.props.addSoundingConversions(meters)
        }
    }

    override fun layers(options: LayerableOptions) = when (options.depth) {
        Depth.FATHOMS -> fathoms()
        Depth.METERS -> meters()
        Depth.FEET -> feet()
    }

    private fun fathoms() = sequenceOf(
        singleText(id = "${key}_fathoms", textKey = "FATHOMS", subTextKey = "FATHOMS_FT"),
        primaryTextLayer(id = "${key}_fathoms_offset", textKey = "FATHOMS", subTextKey = "FATHOMS_FT"),
        subTextLayer(id = "${key}_fathoms_feet_offset", textKey = "FATHOMS_FT"),
    )

    private fun feet() = sequenceOf(
        singleText(id = "${key}_feet", textKey = "FEET"),
    )

    private fun meters() = sequenceOf(
        singleText(id = "${key}_meters", textKey = "METERS_W", subTextKey = "METERS_T"),
        primaryTextLayer(id = "${key}_meters_offset", textKey = "METERS_W", subTextKey = "METERS_T"),
        subTextLayer(id = "${key}_meters_tenths_offset", textKey = "METERS_T"),
    )

    private fun singleText(id: String, textKey: String, subTextKey: String? = null) = Layer(
        id = id,
        type = LayerType.SYMBOL,
        sourceLayer = key,
        filter = subTextKey?.let {
            listOf(
                Filters.all,
                Filters.eqTypePoint,
                listOf(
                    Filters.any,
                    listOf(">=", "METERS", config.deepDepth),
                    listOf("==", subTextKey, 0),
                ),
            )
        } ?: Filters.eqTypePoint,
        layout = Layout(
            textFont = listOf(Font.ROBOTO_BOLD),
            textAnchor = Anchor.CENTER,
            textJustify = Anchor.CENTER,
            textField = listOf("get", textKey),
            textAllowOverlap = true,
            textIgnorePlacement = true,
            textSize = 11f,
            symbolPlacement = Placement.POINT,
        ),
        paint = Paint(
            textColor = textColor,
            textHaloColor = colorFrom("CHWHT"),
            textHaloWidth = 1.5f
        )
    )

    private fun primaryTextLayer(id: String, textKey: String, subTextKey: String) = Layer(
        id = id,
        type = LayerType.SYMBOL,
        sourceLayer = key,
        filter = listOf(
            Filters.all,
            Filters.eqTypePoint,
            listOf(">", subTextKey, 0),
            listOf("<", "METERS", config.deepDepth),
        ),
        layout = Layout(
            textFont = listOf(Font.ROBOTO_BOLD),
            textAnchor = Anchor.BOTTOM_RIGHT,
            //x (neg left / pos right), y (neg up / pos down)
            textOffset = listOf(0.0f, 0.6f),
            textJustify = Anchor.CENTER,
            textField = listOf("get", textKey),
            textAllowOverlap = true,
            textIgnorePlacement = true,
            textSize = 11f,
            symbolPlacement = Placement.POINT,
        ),
        paint = Paint(
            textColor = textColor,
            textHaloColor = colorFrom("CHWHT"),
            textHaloWidth = 1.5f
        )
    )

    private fun subTextLayer(id: String, textKey: String) = Layer(
        id = id,
        type = LayerType.SYMBOL,
        sourceLayer = key,
        filter = listOf(
            Filters.all,
            Filters.eqTypePoint,
            listOf(">", textKey, 0),
            listOf("<", "METERS", config.deepDepth),
        ),
        layout = Layout(
            textFont = listOf(Font.ROBOTO_BOLD),
            textAnchor = Anchor.TOP_LEFT,
            //x (neg left / pos right), y (neg up / pos down)
            textOffset = listOf(0.1f, 0.0f),
            textJustify = Anchor.CENTER,
            textField = listOf("get", textKey),
            textAllowOverlap = true,
            textIgnorePlacement = true,
            textSize = 9f,
            symbolPlacement = Placement.POINT,
        ),
        paint = Paint(
            textColor = textColor,
        )
    )

    private val textColor = listOf(
        "case", listOf("<=", listOf("get", "METERS"), config.deepDepth),
        colorFrom("SNDG2"), // dark
        colorFrom("SNDG1")  // light
    )
}

