package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Pipeline, submarine / on land
 *
 * Acronym: PIPSOL
 *
 * Code: 86
 */
class Pipsol : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 1f, style = LineStyle.CustomDash(5f, 3f)),
    )
}
