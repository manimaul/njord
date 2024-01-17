package io.madrona.njord.layers

import io.madrona.njord.model.Anchor
import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point
 *
 * Object: Coastguard station
 *
 * Acronym: CGUSTA
 *
 * Code: 29
 */
class Cgusta : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CGUSTA02)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
           anchor = Anchor.BOTTOM_LEFT
        ),
    )
}
