package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Local magnetic anomaly
 *
 * Acronym: LOCMAG
 *
 * Code: 76
 */
class Locmag : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.LOCMAG01)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
