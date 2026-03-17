package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.model.*
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
                iconSize = 1.0f,
                iconRotationAlignment = IconRotationAlignment.MAP,
            )
        ),
    )
}
