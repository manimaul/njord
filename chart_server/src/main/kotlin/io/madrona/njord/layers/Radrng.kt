package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Radar range
 *
 * Acronym: RADRNG
 *
 * Code: 100
 */
class Radrng : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(Color.TRFCF)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            color = Color.TRFCF,
            theme = options.theme,
            width = 1f,
            style = LineStyle.DashLine,
        )
    )
}
