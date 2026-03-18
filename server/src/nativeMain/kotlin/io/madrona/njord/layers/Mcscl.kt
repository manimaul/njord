package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Compilation scale of data
 *
 * Acronym: M_CSCL
 *
 * Code: 303
 */
class Mcscl : Layerable("M_CSCL") {

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 1f, style = LineStyle.CustomDash(5f, 3f)),
    )
}
