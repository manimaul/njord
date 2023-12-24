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
    override fun preTileEncode(feature: ChartFeature) { }

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_fill",
            type = LayerType.FILL,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                fillColor = colorFrom(Color.TRFCF, options.theme)
            ),
        ),
    )
}
