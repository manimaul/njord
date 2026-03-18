package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Harbour facility
 *
 * Acronym: HRBFAC
 *
 * Code: 65
 */
class Hrbfac : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.HRBFAC09)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillColor(theme = options.theme, color = Color.CHMGF),
    )
}
