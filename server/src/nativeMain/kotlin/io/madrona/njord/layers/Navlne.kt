package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Navigation line
 *
 * Acronym: NAVLNE
 *
 * Code: 85
 */
class Navlne : Layerable() {

    private val lineColor = Color.CHGRD
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            lineLayerWithColor(
                theme = options.theme,
                color = lineColor,
                width = 1f,
                style = LineStyle.CustomDash(10f, 5f)),
            lineLayerWithLabel(theme = options.theme, label = Label.Property("INFORM")),
        )
    }
}
