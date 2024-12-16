package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Traffic Separation Zone
 *
 * Acronym: TSEZNE
 *
 * Code: 150
 */
class Tsezne : Layerable() {
    private val ac = Color.TRFCF
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(ac)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(ac, options.theme),
    )
}
