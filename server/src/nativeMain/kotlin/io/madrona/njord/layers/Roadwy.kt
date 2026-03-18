package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Road
 *
 * Acronym: ROADWY
 *
 * Code: 111
 */
class Roadwy : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.CHBLK),
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 1f),
    )
}
