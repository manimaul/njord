package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Pile
 *
 * Acronym: PILPNT
 *
 * Code: 90
 */
class Pilpnt : Layerable() {
    val symbol = Sprite.PILPNT02

    override suspend fun preTileEncode(feature: ChartFeature) {
       feature.pointSymbol(symbol)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            pointLayerFromSymbol(
                anchor = Anchor.CENTER,
                iconRotationAlignment = IconRotationAlignment.MAP,
            ),
        )
    }
}