package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Rescue station
 *
 * Acronym: RSCSTA
 *
 * Code: 113
 */
class Rscsta : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.RSCSTA02)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
