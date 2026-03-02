package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Traffic control point
 *
 * Acronym: TCTLPT
 *
 * Code: 144
 */
class Tctlpt : Layerable() {
    private val symbol = Sprite.CHKPNT01

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(symbol)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            symbol = Symbol.Sprite(symbol),
            anchor = Anchor.CENTER,
        )
    )
}
