package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Area
 *
 * Object: Lock basin
 *
 * Acronym: LOKBSN
 *
 * Code: 79
 */
open class Lokbsn(customKey: String? = null) : Layerable(customKey) {
    private val areaColor = Color.DEPVS
    private val lineColor = Color.CHBLK

    override fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(areaColor)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(areaColor),
        lineLayerWithColor(lineColor, width = 1f)
    )
}
