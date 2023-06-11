package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Anchorage area
 *
 * Acronym: ACHARE
 *
 * Code: 4
 */
class Achare(
    customKey: String? = null
) : Layerable(customKey) {
    private val lineColor = Color.CHMGF
    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.ACHARE02)
        feature.linePattern(Sprite.ACHARE02)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(color = lineColor, width = 2f, style = LineStyle.CustomDash(3f, 4f)),
        lineLayerWithPattern()
    )
}
