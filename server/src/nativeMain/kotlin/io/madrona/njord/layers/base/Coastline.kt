package io.madrona.njord.layers.base

import io.madrona.njord.layers.Layerable
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.model.Color

/**
 * Natural Earth base layer: coastline
 */
class Coastline : Layerable("coastline") {
    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.CSTLN, width = 1f),
    )
}