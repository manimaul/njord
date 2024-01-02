package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Fog signal
 *
 * Acronym: FOGSIG
 *
 * Code: 58
 */
class Fogsig : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.FOGSIG01)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            iconOffset = listOf(-10f, 10f),
            iconAllowOverlap = true,
            iconKeepUpright = true,
        )
    )
}
