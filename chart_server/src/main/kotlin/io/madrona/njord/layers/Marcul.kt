package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Marine farm/culture
 *
 * Acronym: MARCUL
 *
 * Code: 82
 */
class Marcul : Layerable() {
    private val lineColor = Color.CHGRD
    private val symbol = Sprite.MARCUL02
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
        feature.areaPattern(symbol)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.DashLine, width = 2f),
        pointLayerFromSymbol(symbol = symbol),
        areaLayerWithSingleSymbol(symbol = symbol),
    )
}
