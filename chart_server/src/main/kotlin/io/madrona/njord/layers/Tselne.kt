package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Traffic Separation Line
 *
 * Acronym: TSELNE
 *
 * Code: 145
 */
class Tselne : Layerable() {
    private val lineColor = Color.TRFCF
    override fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(color = lineColor, width = 6f)
    )
}
