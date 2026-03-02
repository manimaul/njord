package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Sloping ground
 *
 * Acronym: SLOGRD
 *
 * Code: 127
 */
class Slogrd : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {}

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(color = Color.LANDF, theme = options.theme),
        lineLayerWithColor(theme = options.theme, color = Color.CSTLN),
    )
}
