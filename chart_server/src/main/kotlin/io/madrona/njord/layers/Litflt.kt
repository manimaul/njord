package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Light float
 *
 * Acronym: LITFLT
 *
 * Code: 76
 */
class Litflt : Layerable() {

    private val symbol = Sprite.LITFLT02
    override suspend fun preTileEncode(feature: ChartFeature) {
       feature.pointSymbol(symbol)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(symbol)
    )
}
