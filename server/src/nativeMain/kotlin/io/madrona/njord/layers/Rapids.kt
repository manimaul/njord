package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Rapids
 *
 * Acronym: RAPIDS
 *
 * Code: 107
 */
class Rapids : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.DEPMD),
        lineLayerWithColor(theme = options.theme, color = Color.DEPMD, width = 1f, style = LineStyle.CustomDash(5f, 3f)),
    )
}
