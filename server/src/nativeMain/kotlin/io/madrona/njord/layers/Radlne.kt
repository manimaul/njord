package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Radar line
 *
 * Acronym: RADLNE
 *
 * Code: 99
 */
class Radlne : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(Color.TRFCD)
        feature.bearingLabel()
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            color = Color.TRFCD,
            theme = options.theme,
            style = LineStyle.DashLine,
        ),
        bearingLabelLayer(options),
    )
}
