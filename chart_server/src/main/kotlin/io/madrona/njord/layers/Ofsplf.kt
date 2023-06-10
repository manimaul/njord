package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Offshore platform
 *
 * Acronym: OFSPLF
 *
 * Code: 87
 */
class Ofsplf : Layerable() {
    private val areaColor = Color.CHBRN
    private val lineColor = Color.CSTLN

    override fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(areaColor)
        feature.lineColor(lineColor)
        feature.pointSymbol(Sprite.OFSPLF01)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillColor(areaColor),
        lineLayerWithColor(lineColor, width = 4f)
    )
}
