package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Pipeline overhead
 *
 * Acronym: PIPOHD
 *
 * Code: 85
 */
class Pipohd : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 1f),
    )
}
