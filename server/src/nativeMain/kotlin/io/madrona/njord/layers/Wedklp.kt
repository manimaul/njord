package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Weed/Kelp
 *
 * Acronym: WEDKLP
 *
 * Code: 158
 */
class Wedklp : Layerable() {
    private val lineColor = Color.CHGRF

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.WEDKLP03)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithSingleSymbol(),
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.DashLine, width = 1f)
    )
}
