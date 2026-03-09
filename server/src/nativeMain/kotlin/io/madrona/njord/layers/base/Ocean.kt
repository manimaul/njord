package io.madrona.njord.layers.base

import io.madrona.njord.layers.Layerable
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.model.Color

/**
 * Natural Earth base layer: ocean polygon fill
 */
class Ocean : Layerable("ocean") {
    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(Color.DEPDW, options.theme),
    )
}