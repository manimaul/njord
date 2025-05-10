package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Inshore traffic zone
 *
 * Acronym: ISTZNE
 *
 * Code: 68
 */
class Istzne : Layerable() {
    private val lineColor = Color.TRFCD
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.DashLine, width = 1f),
    )
}
