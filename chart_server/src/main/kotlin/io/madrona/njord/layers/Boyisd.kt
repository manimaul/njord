package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Buoy, isolated danger
 *
 * Acronym: BOYISD
 *
 * Code: 16
 */
class Boyisd : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.BOYISD12)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol()
    )
}
