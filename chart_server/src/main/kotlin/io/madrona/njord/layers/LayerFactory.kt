package io.madrona.njord.layers

import io.madrona.njord.layers.set.BaseLayers
import io.madrona.njord.layers.set.ExtraLayers
import io.madrona.njord.layers.set.StandardLayers
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Layer

class LayerFactory(
    private val baseLayers: BaseLayers = BaseLayers(),
    private val standardLayers: StandardLayers = StandardLayers(),
    private val extraLayers: ExtraLayers = ExtraLayers(),
) {

    private val layerables by lazy {
        baseLayers.layers + standardLayers.layers + extraLayers.layers
    }

    private val layers: Map<LayerableOptions, Sequence<Layer>> by lazy {
        Depth.values().map { LayerableOptions(it) }.associateWith { options ->
            layerables.map {
                it.layers(options)
            }.flatten()
        }
    }

    fun layers(options: LayerableOptions) = layers[options]?.toList() ?: throw RuntimeException(
        "layers not available for options $options"
    )

    fun tileEncode(feature: ChartFeature) {
        layerables.forEach { it.addTileEncodings(feature) }
    }
}
