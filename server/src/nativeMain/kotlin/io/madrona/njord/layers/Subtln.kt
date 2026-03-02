package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Submarine transit lane
 *
 * Acronym: SUBTLN
 *
 * Code: 136
 */
class Subtln : Layerable() {
    private val lineColor = Color.TRFCF

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.CustomDash(3f, 2f)),
    )
}
