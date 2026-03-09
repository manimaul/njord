package io.madrona.njord.layers.base

import io.madrona.njord.layers.Layerable
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.model.Color

/**
 * Natural Earth base layer: bathymetry depth area polygons
 */
class Bathymetry : Layerable("bathymetry") {
    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(Color.DEPMD, options.theme),
    )
}