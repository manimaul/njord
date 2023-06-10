package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Line
 *
 * Object: Deep water route centerline
 *
 * Acronym: DWRTCL
 *
 * Code: 40
 */
class Dwrtcl : Layerable() {
    private val lineColor = Color.CHBLK

    override fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(lineColor)
    )
}