package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Floating dock
 *
 * Acronym: FLODOC
 *
 * Code: 57
 */
class Flodoc : Layerable() {
    private val areaColor = Color.CHBRN
    private val lineColor = Color.CSTLN

    override fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(areaColor)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = areaColor),
        lineLayerWithColor(theme = options.theme, color = lineColor)
    )
}
