package io.madrona.njord.layers

import io.madrona.njord.model.Anchor
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Anchor
 *
 * Acronym: ACHPNT
 *
 * Code: 1003
 *
 * S-52 display: SY(ACHPNT01) — rendered using ACHARE02 (identical symbol)
 */
class Achpnt : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.ACHARE02)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            symbol = Symbol.Sprite(Sprite.ACHARE02),
            anchor = Anchor.CENTER
        ),
    )
}
