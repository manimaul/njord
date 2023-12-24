package io.madrona.njord.layers

import io.madrona.njord.model.*

class Ponton : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            areaLayerWithFillColor(theme = options.theme, color = Color.CHBRN),
            lineLayerWithColor(theme = options.theme, color = Color.CSTLN, width = 2f),
        )
    }
}