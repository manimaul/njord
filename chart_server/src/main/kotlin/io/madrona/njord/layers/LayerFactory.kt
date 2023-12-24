package io.madrona.njord.layers

import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.layers.set.ExtraLayers
import io.madrona.njord.layers.set.InlandLayers
import io.madrona.njord.layers.set.StandardLayers
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Layer

class LayerFactory(
    private val standardLayers: StandardLayers = StandardLayers(),
    private val inlandLayers: InlandLayers = InlandLayers(),
    private val extraLayers: ExtraLayers = ExtraLayers(),
    private val config: ChartsConfig = Singletons.config,
) {

    private val layerables by lazy {
        (standardLayers.layers + inlandLayers.layers + extraLayers.layers).let {
            if (config.debugTile) {
                it + sequenceOf(Debug())
            } else {
                it
            }
        }
    }

    private val layers: Map<LayerableOptions, Sequence<Layer>> by lazy {
        Depth.values().map { depth ->
            Theme.values().map { theme ->
                LayerableOptions(depth, theme)
            }
        }.flatten().associateWith { options ->
            layerables.map {
                it.layers(options)
            }.flatten()
        }
    }

    fun layers(options: LayerableOptions) = layers[options]?.toList() ?: throw RuntimeException(
        "layers not available for options $options"
    )

    fun preTileEncode(feature: ChartFeature) {
        layerables.forEach {
            if (feature.layer == it.key) {
                it.preTileEncode(feature)
            }
        }
    }
}
