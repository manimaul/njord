package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Tideway
 *
 * Acronym: TIDEWY
 *
 * Code: 147
 */
class Tidewy : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.DEPMD),
        lineLayerWithColor(theme = options.theme, color = Color.DEPMD, width = 1f),
    )
}
