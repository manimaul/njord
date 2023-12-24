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

    override fun preTileEncode(feature: ChartFeature) {
       feature.pointSymbol(symbol)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_point",
                type = LayerType.CIRCLE,
                sourceLayer = key,
                filter = listOf(
                    Filters.any,
                    Filters.eqTypePoint,
                ),
                paint = Paint(
                    circleColor = colorFrom(Color.CHBLK, options.theme),
                    circleRadius = 2.5f
                )
            )
        )
    }
}