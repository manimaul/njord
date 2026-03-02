package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Traffic separation scheme roundabout
 *
 * Acronym: TSSRON
 *
 * Code: 150
 */
class Tssron : Layerable() {
    private val lineColor = Color.TRFCF

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.CustomDash(3f, 2f)),
    )
}
