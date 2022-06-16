package io.madrona.njord.layers

import io.madrona.njord.layers.set.BaseLayers
import io.madrona.njord.layers.set.ExtraLayers
import io.madrona.njord.layers.set.StandardLayers

class LayerFactory(
    private val baseLayers: BaseLayers = BaseLayers(),
    private val standardLayers: StandardLayers = StandardLayers(),
    private val extraLayers: ExtraLayers = ExtraLayers(),
) {
    fun layers(options: LayerableOptions) = (
            baseLayers.layers + standardLayers.layers + extraLayers.layers).map {
        it.layers(options)
    }.flatten().toList()
}