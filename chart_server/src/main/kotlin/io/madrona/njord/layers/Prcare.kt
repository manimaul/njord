package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Precautionary area
 *
 * Acronym: PRCARE
 *
 * Code: 96
 */
class Prcare : Layerable() {

    private val lineColor = Color.TRFCF
    private val symbol = Sprite.PRCARE12
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(symbol)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            symbol  = symbol
        ),
        lineLayerWithColor(
            theme = options.theme,
            color = lineColor,
            width = 2f,
            style = LineStyle.CustomDash(3f, 2f)
        ),
        areaLayerWithPointSymbol(
            symbol = symbol,
            iconOffset = listOf(30f, 30f), // give room for lights / buoys
        ),
    )
}
