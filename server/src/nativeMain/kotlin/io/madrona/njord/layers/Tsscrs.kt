package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Traffic separation scheme crossing
 *
 * Acronym: TSSCRS
 *
 * Code: 147
 */
class Tsscrs : Layerable() {
    private val lineColor = Color.TRFCF

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.CustomDash(3f, 2f)),
    )
}
