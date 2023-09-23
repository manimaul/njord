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
    override fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            lineLayerWithColor(lineColor, width = 1f, style = LineStyle.CustomDash(10f, 5f)),
            lineLayerWithLabel(Label.Property("INFORM")),
        )
    }
}
