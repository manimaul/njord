package io.madrona.njord.layers

import io.madrona.njord.model.*

class Iceare : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_fill",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = Filters.eqTypePolyGon,
                paint = Paint(
                    fillPattern = "ICEARE04"
                )
            ),
        )
    }
}