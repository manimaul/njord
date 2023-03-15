package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Ice area
 *
 * Acronym: ICEARE
 *
 * Code: 66
 */
class Iceare : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.props["AP"] = "ICEARE04"
    }
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_fill",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = Filters.eqTypePolyGon,
                paint = Paint(
                    fillPattern = listOf("get", "AP")
                )
            ),
        )
    }
}