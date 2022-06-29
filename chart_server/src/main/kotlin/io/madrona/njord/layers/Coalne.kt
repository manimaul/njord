package io.madrona.njord.layers

import io.madrona.njord.model.*

class Coalne : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = Filters.eqTypeLineString,
                paint = Paint(
                    lineColor = colorFrom("CSTLN"),
                    lineWidth = 1f,
                    lineDashArray = listOf(5f, 5f)
                )
            )
        )
    }
}