package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Contiguous zone
 *
 * Acronym: CONZNE
 *
 * Code: 33
 */
class Conzne : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.CHMGF),
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 1f, style = LineStyle.CustomDash(5f, 3f)),
    )
}
