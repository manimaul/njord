package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives:  Area
 *
 * Object: Lake
 *
 * Acronym: LAKARE
 *
 * Code: 69
 */
class Lakare : Layerable() {
    private val lineColor = Color.CSTLN

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
        feature.areaColor(Color.DEPDW)
        feature.lineColor(Color.CSTLN)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(Color.DEPDW, options.theme),
        lineLayerWithColor(theme = options.theme, color = Color.CSTLN, style = LineStyle.Solid, width = 1f),
    )
}
