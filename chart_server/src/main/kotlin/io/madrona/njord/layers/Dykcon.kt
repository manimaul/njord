package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Dyke
 *
 * Acronym: DYKCON
 *
 * Code: 49
 */
class Dykcon : Layerable() {
    private val lineColor = Color.LANDF
    private val areaColor = Color.CHBRN

    override fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(color = areaColor)
        feature.lineColor(color = lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(color = lineColor),
        areaLayerWithFillColor(color = areaColor)
    )
}
