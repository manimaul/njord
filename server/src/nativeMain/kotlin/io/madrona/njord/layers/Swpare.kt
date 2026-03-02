package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Swept area
 *
 * Acronym: SWPARE
 *
 * Code: 141
 */
class Swpare : Layerable() {
    private val lineColor = Color.CHBLK

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.DashLine),
    )
}
