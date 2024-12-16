package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Built-up area
 *
 * Acronym: BUAARE
 *
 * Code: 13
 */
class Buaare : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.BUAARE02)
        feature.areaColor(Color.LANDF)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.CHBRN),
        lineLayerWithColor(theme = options.theme, color = Color.CSTLN, width = 1f),
        pointLayerFromSymbol(),
    )
}
