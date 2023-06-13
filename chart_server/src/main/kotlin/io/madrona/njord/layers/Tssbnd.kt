package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Traffic Separation Scheme Boundary
 *
 * Acronym: TSSBND
 *
 * Code: 146
 */
class Tssbnd : Layerable() {
    private val lineColor = Color.TRFCF

    override fun preTileEncode(feature: ChartFeature) {
       feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(color = lineColor, style = LineStyle.CustomDash(3f, 2f))
    )
}