package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Trafic
import io.madrona.njord.model.*
import io.madrona.njord.layers.attributehelpers.Trafic.Companion.trafic

/**
 * Geometry Primitives: Area
 *
 * Object: Two-way route part
 *
 * Acronym: TWRTPT
 *
 * Code: 152
 */
class Twrtpt : Layerable() {
    private val lineColor = Color.TRFCF
    override fun preTileEncode(feature: ChartFeature) {
        when(feature.trafic()) {
            Trafic.INBOUND,
            Trafic.OUTBOUND,
            Trafic.ONE_WAY -> feature.pointSymbol(Sprite.TWRTPT53)
            Trafic.TWO_WAY -> feature.pointSymbol(Sprite.TWRTPT52)
            null -> feature.pointSymbol(Sprite.TWRDEF51)
        }

        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            theme = options.theme,
            color = lineColor,
            width = 2f,
            style = LineStyle.CustomDash(3f, 2f)
        ),
        areaLayerWithPointSymbol(
            iconRotate = listOf("get", "ORIENT"),
            iconRotationAlignment = IconRotationAlignment.MAP,
            iconAllowOverlap = false,
        ),
    )
}
