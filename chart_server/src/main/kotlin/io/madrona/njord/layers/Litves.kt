package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Light vessel
 *
 * Acronym: LITVES
 *
 * Code: 77
 */
class Litves : LayerableTodo() {

    private val symbol = Sprite.LITVES02
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(symbol)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            symbol = Symbol.Sprite(symbol),
        )
    )
}
