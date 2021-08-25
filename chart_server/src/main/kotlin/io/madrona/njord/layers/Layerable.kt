package io.madrona.njord.layers

import io.madrona.njord.model.Layer
import io.madrona.njord.model.StyleColor

interface Layerable {
    val key: String
    fun layers(color: StyleColor): Sequence<Layer>
}