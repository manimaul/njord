package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Underwater rock / awash rock
 *
 * Acronym: UWTROC
 *
 * Code: 152
 */
class Uwtroc : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.OBSTRN01)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
