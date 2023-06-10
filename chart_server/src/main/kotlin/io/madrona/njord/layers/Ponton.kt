package io.madrona.njord.layers

import io.madrona.njord.model.*

class Ponton : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            areaLayerWithFillColor(color = Color.CHBRN),
            lineLayerWithColor(color = Color.CSTLN, width = 2f),
        )
    }
}