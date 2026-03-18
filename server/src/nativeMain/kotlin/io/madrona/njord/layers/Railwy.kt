package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Railway
 *
 * Acronym: RAILWY
 *
 * Code: 105
 */
class Railwy : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 1f, style = LineStyle.CustomDash(5f, 3f)),
    )
}
