package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Dam
 *
 * Acronym: DAMCON
 *
 * Code: 38
 */
class Damcon : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CHINFO06)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(theme = options.theme, color = Color.CSTLN),
        areaLayerWithFillColor(theme = options.theme, color = Color.LANDF)
    )
}
