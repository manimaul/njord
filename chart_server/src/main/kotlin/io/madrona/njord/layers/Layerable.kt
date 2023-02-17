package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Layer
import io.madrona.njord.util.logger

abstract class Layerable {
    val log = logger()
    val key = javaClass.simpleName.uppercase()
    abstract fun layers(options: LayerableOptions): Sequence<Layer>

    open fun preTileEncode(feature: ChartFeature) {
        log.warn("layer $key preTileEncode not handled")
    }
}

data class LayerableOptions(
    val depth: Depth
)
