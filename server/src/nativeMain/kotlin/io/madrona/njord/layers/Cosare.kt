package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Coastline area
 *
 * Acronym: COSARE
 *
 * Code: 38
 */
class Cosare : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.CHMGF),
        lineLayerWithColor(theme = options.theme, color = Color.CHMGF, width = 1f),
    )
}
