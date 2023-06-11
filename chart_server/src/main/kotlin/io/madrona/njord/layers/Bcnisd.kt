package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Beacon, isolated danger
 *
 * Acronym: BCNISD
 *
 * Code: 6
 */
class Bcnisd : Layerable() {

    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.BCNISD21)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
