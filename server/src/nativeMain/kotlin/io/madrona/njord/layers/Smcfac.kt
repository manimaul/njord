package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Small craft facility
 *
 * Acronym: SMCFAC
 *
 * Code: 128
 */
class Smcfac : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.SMCFAC02)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillColor(theme = options.theme, color = Color.CHMGF),
    )
}
