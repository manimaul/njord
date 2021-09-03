package io.madrona.njord.layers

import io.madrona.njord.model.StyleColor

class LayerFactory(
        private val layerables: Sequence<Layerable> = sequenceOf(
                Background(),
                Seaare(),
                Depare(),
                Depcnt(),
                Slcons(),
                Ponton(),
                Hulkes(),
                Lndare(),
                Soundg(),
                Boyspp(),
                Lights()
        )
) {

    fun layers(options: LayerableOptions) = layerables.map {
        it.layers(options)
    }.flatten().toList()
}