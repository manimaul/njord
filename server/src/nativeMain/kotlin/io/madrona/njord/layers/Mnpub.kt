package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Non-publishing area (meta)
 *
 * Acronym: M_NPUB
 *
 * Code: 306
 */
class Mnpub : Layerable("M_NPUB") {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CHINFO07)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
