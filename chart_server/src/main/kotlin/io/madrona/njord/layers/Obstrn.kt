package io.madrona.njord.layers

import io.madrona.njord.model.*

class Obstrn : Layerable {
    override val key = "OBSTRN"

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_fill_pattern",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = Filters.eqTypePolyGon,
                paint = Paint(
                    fillPattern = listOf("get", "SY")
                )
            ),
        )
    }
}