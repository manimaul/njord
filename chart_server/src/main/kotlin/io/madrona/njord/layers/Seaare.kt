package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Sea area / named water area
 *
 * Acronym: SEAARE
 *
 * Code: 119
 */
class Seaare : Layerable() {
//    override suspend fun preTileEncode(feature: ChartFeature) {
//    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            areaLayerWithText("OBJNAM", options.theme),
        )
    }
}