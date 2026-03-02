package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Recommended track center line
 *
 * Acronym: RCRTCL
 *
 * Code: 107
 */
class Rcrtcl : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {}

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.PLRTE),
    )
}
