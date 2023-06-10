package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Ice area
 *
 * Acronym: ICEARE
 *
 * Code: 66
 */
class Iceare : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.ICEARE04)
    }
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            pointLayerFromSymbol(),
        )
    }
}