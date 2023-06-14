package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Ferry route
 *
 * Acronym: FERYRT
 *
 * Code: 53
 */
class Feryrt : Layerable() {
    private val lineColor = Color.CHBLK
    override fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
        feature.linePattern(Sprite.FRYARE51)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            color = lineColor,
            style = LineStyle.CustomDash(6f, 2f)
        ),
        lineLayerWithPattern(Sprite.FRYARE51)
    )
}
