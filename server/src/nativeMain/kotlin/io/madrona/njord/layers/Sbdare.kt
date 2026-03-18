package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Seabed area
 *
 * Acronym: SBDARE
 *
 * Code: 116
 */
class Sbdare : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.NODTA),
    )
}
