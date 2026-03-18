package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Harbour area
 *
 * Acronym: HRBARE
 *
 * Code: 64
 */
class Hrbare : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.CHMGF),
        lineLayerWithColor(theme = options.theme, color = Color.CHMGF, width = 2f),
    )
}
