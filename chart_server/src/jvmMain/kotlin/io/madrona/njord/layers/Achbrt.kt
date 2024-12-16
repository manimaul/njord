package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Anchor berth
 *
 * Acronym: ACHBRT
 *
 * Code: 3
 */
class Achbrt(customKey: String? = null) : Layerable(customKey) {
    private val lineColor = Color.CHMGF

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
        feature.pointSymbol(Sprite.ACHBRT07)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(
            theme = options.theme,
            color = lineColor,
            width = 2f,
            style = LineStyle.DashLine
        ),
    )
}