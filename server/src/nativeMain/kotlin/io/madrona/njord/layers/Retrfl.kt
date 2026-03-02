package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Retro-reflector
 *
 * Acronym: RETRFL
 *
 * Code: 112
 */
class Retrfl : Layerable() {
    private val symbol = Sprite.RETRFL02

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(symbol)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            symbol = Symbol.Sprite(symbol),
            anchor = Anchor.CENTER,
        )
    )
}
