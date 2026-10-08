package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.model.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Geometry Primitives: Point
 *
 * Object: Light
 *
 * Acronym: LIGHTS
 *
 * Code: 75
 */
class Lights : Layerable() {

    val dayLineColor = colorFrom(Color.CHBLK, ThemeMode.Day)
    val duskLineColor = colorFrom(Color.CHBLK, ThemeMode.Dusk)
    val nightLineColor = colorFrom(Color.CHBLK, ThemeMode.Night)

    override suspend fun preTileEncode(feature: ChartFeature) {
        val lightColor = when (feature.colors().firstOrNull()) {
            Colour.Red -> {
                feature.pointSymbol(Sprite.LIGHTS11)
                feature.lineColor(Color.LITRD)
                Color.LITRD
            }
            Colour.Green -> {
                feature.pointSymbol(Sprite.LIGHTS12)
                feature.lineColor(Color.LITGN)
                Color.LITGN
            }
            Colour.Yellow,
            Colour.White,
            Colour.Amber,
            Colour.Orange -> {
                feature.pointSymbol(Sprite.LIGHTS13)
                feature.lineColor(Color.LITYW)
                Color.LITYW
            }
            else -> {
                feature.lineColor(Color.LITYW)
                feature.pointSymbol(Sprite.LITDEF11)
                Color.LITYW
            }
        }

        val sectr1 = feature.props["SECTR1"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        val sectr2 = feature.props["SECTR2"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        val valnmr = feature.props["VALNMR"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        val majorLight = valnmr != null && valnmr >= 10.0
        val dayColor = colorFrom(lightColor, ThemeMode.Day)
        val duskColor = colorFrom(lightColor, ThemeMode.Dusk)
        val nightColor = colorFrom(lightColor, ThemeMode.Night)
        val radius = when(lightColor) {
            Color.LITRD -> 68
            Color.LITGN -> 74
            else -> 80
        }

        if (majorLight || (sectr1 != null && sectr2 != null)) {
            val s1 = sectr1 ?: 0.0
            val s2 = sectr2 ?: 0.0
            feature.props["SI"] = JsonPrimitive("sector_${s1}_${s2}_${dayColor}_${duskColor}_${nightColor}_${dayLineColor}_${duskLineColor}_${nightLineColor}_${radius}")
        }

        // S-52 LIGHTS05: "The LITDSN text string is not used for sector lights because it would
        // cause clatter". A major light drawn with an all-round SI arc still gets one.
        if (sectr1 == null || sectr2 == null) {
            LightDescription.from(feature.props)?.let {
                feature.props[DESCRIPTION_HEAD] = it.head.json
                feature.props[DESCRIPTION_TAIL] = it.tail.json
            }
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            anchor = Anchor.BOTTOM,
            iconAllowOverlap = true,
            iconRotate = IconRot.Degrees(135f),
            filter = listOf(Filters.all, Filters.eqTypePoint, listOf("!has", "SI")).json
        ),
        Layer(
            id = "LIGHTS_sector",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = listOf(Filters.all, Filters.eqTypePoint, listOf("has", "SI")).json,
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = listOf("get", "SI").json,
                iconAnchor = Anchor.CENTER,
                iconAllowOverlap = true,
                iconIgnorePlacement = true,
                iconSize = 1.0f,
                iconRotationAlignment = IconRotationAlignment.MAP,
            )
        ),
        description(options),
    )

    /**
     * S-52 LIGHTS05 writes the description with `TX('LITDSN',3,2,3,'15110',2,0,CHBLK,23)` when the
     * flare is at 135° (always, here): left justified, vertically centred, two text bodies right
     * of the light.
     */
    private fun description(options: LayerableOptions) = Layer(
        id = "${key}_description",
        type = LayerType.SYMBOL,
        sourceLayer = sourceLayer,
        filter = listOf(Filters.all, Filters.eqTypePoint, listOf("has", DESCRIPTION_HEAD)).json,
        layout = Layout(
            textFont = listOf(Font.ROBOTO_BOLD),
            textAnchor = Anchor.LEFT,
            textJustify = TextJustify.LEFT,
            textField = descriptionText(options.depth),
            textOffset = Offset.Coord(x = 2f, y = 0f).property,
            textSize = 14f,
            symbolPlacement = Placement.POINT,
        ),
        paint = Paint(
            textColor = colorFrom(Color.SNDG2, options.theme).json,
            textHaloColor = colorFrom(Color.DEPDW, options.theme),
            textHaloWidth = 2.5f
        )
    )

    /**
     * `head + height + tail` - see [LightDescription] for why the height is spliced in here rather
     * than pre-rendered. A zero height is guarded for the same reason as in [ClearanceLabel].
     */
    private fun descriptionText(depth: Depth): JsonElement = listOf(
        "concat",
        listOf("get", DESCRIPTION_HEAD),
        listOf(
            "case",
            listOf("all", listOf("has", "HEIGHT"), listOf(">", listOf("get", "HEIGHT"), 0)),
            height(depth),
            "",
        ),
        listOf("get", DESCRIPTION_TAIL),
    ).json

    /** Metres per the spec's numeric rule: whole, or one decimal when the fraction is non-zero. */
    private fun height(depth: Depth): List<Any> = when (depth) {
        Depth.METERS -> listOf(
            "concat",
            listOf("number-format", listOf("get", "HEIGHT"), upToOneFractionDigit),
            "m",
        )

        Depth.FEET, Depth.FATHOMS -> listOf(
            "concat",
            listOf("to-string", listOf("round", listOf("*", listOf("get", "HEIGHT"), FEET_PER_METER))),
            "ft",
        )
    }

    companion object {
        const val DESCRIPTION_HEAD = "_LDH"
        const val DESCRIPTION_TAIL = "_LDT"
        private const val FEET_PER_METER = 3.28084

        private val upToOneFractionDigit = JsonObject(
            mapOf(
                "locale" to JsonPrimitive("en-US"),
                "min-fraction-digits" to JsonPrimitive(0),
                "max-fraction-digits" to JsonPrimitive(1),
            )
        )
    }
}
