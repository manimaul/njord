package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Oil barrier
 *
 * Acronym: OILBAR
 *
 * Code: 89
 */
class Oilbar : Layerable() {
    private val lineColor = Color.CHBLK

    override fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.DashLine)
    )
}
