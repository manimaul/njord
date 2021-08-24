package io.madrona.njord.layers

import io.madrona.njord.model.StyleColor

class LayerFactory {

    fun layers(color: StyleColor) = sequenceOf(
            Background.layers(),
            Seaare.layers(color),
            Depare.layers(color)
    ).flatten().toList()
}