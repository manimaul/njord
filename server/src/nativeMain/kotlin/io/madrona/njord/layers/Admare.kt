package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Administrative area
 *
 * Acronym: ADMARE
 *
 * Code: 1
 */
class Admare : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.CHMGF),
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 1f, style = LineStyle.CustomDash(5f, 3f)),
    )
}
