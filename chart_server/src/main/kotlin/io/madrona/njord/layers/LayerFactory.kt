package io.madrona.njord.layers

import io.madrona.njord.model.StyleColor

class LayerFactory(
        private val layerables: Sequence<Layerable> = sequenceOf(
                Background(),
                Seaare(),
                Depare(),
        )
) {

    fun layers(color: StyleColor) = layerables.map {
        it.layers(color)
    }.flatten().toList()
}