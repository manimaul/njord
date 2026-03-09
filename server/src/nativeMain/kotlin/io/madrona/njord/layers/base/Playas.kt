package io.madrona.njord.layers.base

import io.madrona.njord.layers.Layerable
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.model.Color

/**
 * Natural Earth base layer: playas (dry lake beds)
 */
class Playas : Layerable("playas") {
    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(Color.LANDA, options.theme),
        lineLayerWithColor(theme = options.theme, color = Color.CSTLN, width = 0.5f),
    )
}