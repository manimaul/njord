package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Area
 *
 * Object: Dock area
 *
 * Acronym: DOCARE
 *
 * Code: 45
 */
class Docare : Layerable() {
    private val areaColor = Color.DEPVS
    private val lineColor = Color.CHGRD

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(areaColor)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = areaColor),
        lineLayerWithColor(theme = options.theme, color = lineColor, width = 0.5f),
    )
}
