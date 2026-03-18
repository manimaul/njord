package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Dry dock
 *
 * Acronym: DRYDOC
 *
 * Code: 46
 */
class Drydoc : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.CHMGF),
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 2f, style = LineStyle.CustomDash(5f, 3f)),
    )
}
