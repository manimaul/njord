package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: New object
 *
 * Acronym: NEWOBJ
 *
 * Code: 89
 */
class Newobj : Layerable() {
    private val symbol = Sprite.NEWOBJ01

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(symbol)
        feature.lineColor(Color.CHBLK)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(symbol = Symbol.Sprite(symbol), anchor = Anchor.CENTER),
        areaLayerWithPointSymbol(anchor = Anchor.CENTER),
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK),
    )
}
