package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Signal station, warning
 *
 * Acronym: SISTAW
 *
 * Code: 124
 */
class Sistaw : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.SISTAW03)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
