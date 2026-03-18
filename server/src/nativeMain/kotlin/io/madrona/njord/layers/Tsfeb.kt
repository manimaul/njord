package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Tidal stream — flood/ebb
 *
 * Acronym: TS_FEB
 *
 * Code: 149
 */
class Tsfeb : Layerable("TS_FEB") {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CURDEF01)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
