package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Coverage
 *
 * Acronym: M_COVR
 *
 * Code: 302
 */
class Mcovr : Layerable("M_COVR") {

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 1f, style = LineStyle.CustomDash(5f, 3f)),
    )
}
