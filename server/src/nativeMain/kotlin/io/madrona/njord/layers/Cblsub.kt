package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Cable, submarine
 *
 * Acronym: CBLSUB
 *
 * Code: 22
 */
class Cblsub : Layerable() {
    private val lineColor = Color.CHMGD
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = lineColor, width = 1f, style = LineStyle.DashLine)
    )
}
