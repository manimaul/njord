package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line
 *
 * Object: Waterfall
 *
 * Acronym: WATFAL
 *
 * Code: 157
 */
class Watfal : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.DEPMD, width = 1f),
    )
}
