package io.madrona.njord.layers

import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.layers.set.BaseLayers
import io.madrona.njord.layers.set.ExtraLayers
import io.madrona.njord.layers.set.InlandLayers
import io.madrona.njord.layers.set.StandardLayers
import io.madrona.njord.model.*

class LayerFactory(
    private val baseLayers: BaseLayers = BaseLayers(),
    private val standardLayers: StandardLayers = StandardLayers(),
    private val inlandLayers: InlandLayers = InlandLayers(),
    private val extraLayers: ExtraLayers = ExtraLayers(),
    private val config: ChartsConfig = Singletons.config,
    private val colorLibrary: ColorLibrary = Singletons.colorLibrary,
) {

    private val layerablesMap: Map<String, Layerable> by lazy {
        (baseLayers.layers + standardLayers.layers + inlandLayers.layers + extraLayers.layers).let {
            if (config.debugTile) {
                it + sequenceOf(Debug())
            } else {
                it
            }
        }.associateBy { it.key }
    }

    private val layers: Map<LayerableOptions, Sequence<Layer>> by lazy {
        val optionList = Depth.entries.flatMap { depth ->
            ThemeMode.entries.map { theme ->
                LayerableOptions(depth, theme)
            }
        } + colorLibrary.colorMap.custom.keys.flatMap { name ->
            Depth.entries.flatMap { depth ->
                ThemeMode.entries.map { themeMode ->
                    LayerableOptions(depth, CustomTheme(themeMode, name))
                }
            }
        }

        optionList.associateWith { options ->
            layerablesMap.values.asSequence().flatMap {
                it.layers(options)
            }
        }
    }

    fun layers(options: LayerableOptions) = layers[options]?.toList() ?: throw RuntimeException(
        "layers not available for options $options"
    )

    suspend fun preTileEncode(feature: ChartFeature) : ChartFeature {
        layerablesMap[feature.layer]?.preTileEncode(feature)
        return feature
    }
}
