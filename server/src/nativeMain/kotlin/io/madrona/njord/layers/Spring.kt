package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Spring, natural
 *
 * Acronym: SPRING
 *
 * Code: 132
 */
class Spring : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.SPRING02)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
