package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Signal station, traffic
 *
 * Acronym: SISTAT
 *
 * Code: 123
 */
class Sistat : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.SISTAT03)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
