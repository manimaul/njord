package io.madrona.njord.layers

import io.madrona.njord.model.Depth
import io.madrona.njord.model.Layer
import io.madrona.njord.model.StyleColor

interface Layerable {
    val key: String
    fun layers(options: LayerableOptions): Sequence<Layer>
}

data class LayerableOptions(
        val color: StyleColor,
        val depth: Depth
)