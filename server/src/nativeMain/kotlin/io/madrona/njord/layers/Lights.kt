package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.model.*

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

    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.colors().firstOrNull()) {
            Colour.Red -> {
                feature.pointSymbol(Sprite.LIGHTS11)
                feature.lineColor(Color.LITRD)
            }
            Colour.Green -> {
                feature.pointSymbol(Sprite.LIGHTS12)
                feature.lineColor(Color.LITGN)
            }
            Colour.Yellow,
            Colour.White,
            Colour.Amber,
            Colour.Orange -> {
                feature.pointSymbol(Sprite.LIGHTS13)
                feature.lineColor(Color.LITYW)
            }
            else -> {
                feature.lineColor(Color.LITYW)
                feature.pointSymbol(Sprite.LITDEF11)
            }
        }
    }

    private val sectorLineColors = setOf(
        Color.LITGN,
        Color.LITRD,
        Color.LITYW,
    )

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            anchor = Anchor.BOTTOM,
            iconAllowOverlap = true,
            iconRotate = IconRot.Degrees(135f),
        ),
        lineLayerWithColor(
            options = sectorLineColors,
            theme = options.theme,
            width = 6.0f,
            filter = listOf(Filters.eq, listOf("get", "SARC"), true).json,
        ),
        lineLayerWithColor(
            color = Color.CHGRD,
            theme = options.theme,
            style = LineStyle.CustomDash(4f, 4f),
            width = 2.0f,
            layout = Layout(lineJoin = LineJoin.ROUND, lineCap = LineCap.BUTT),
            filter = listOf(Filters.eq, listOf("get", "SRAD"), true).json,
        ),
    )
}
