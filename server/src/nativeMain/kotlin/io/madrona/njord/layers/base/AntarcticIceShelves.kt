package io.madrona.njord.layers.base

import io.madrona.njord.layers.Layerable
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.model.Color

/**
 * Natural Earth base layer: antarctic ice shelf polygons
 */
class AntarcticIceShelves : Layerable("antarctic_ice_shelves_polys") {
    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(Color.CHWHT, options.theme),
        lineLayerWithColor(theme = options.theme, color = Color.CSTLN, width = 0.5f),
    )
}