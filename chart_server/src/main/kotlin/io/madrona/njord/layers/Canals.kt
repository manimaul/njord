package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Canal
 *
 * Acronym: CANALS
 *
 * Code: 23
 */
open class Canals : Layerable() {
    open val fillColor = Color.DEPVS

    override fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(fillColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(color = Color.CHBLK, width = 0.5f),
        areaLayerWithFillColor(color = fillColor)
    )
}
