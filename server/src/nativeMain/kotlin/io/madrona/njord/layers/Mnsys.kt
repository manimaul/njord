package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Navigational system of marks
 *
 * Acronym: M_NSYS
 *
 * Code: 307
 */
class Mnsys : Layerable("M_NSYS") {

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 1f, style = LineStyle.CustomDash(5f, 3f)),
    )
}
