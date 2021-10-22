package io.madrona.njord.layers

import io.madrona.njord.model.*

class Coalne : Layerable {
    override val key = "COALNE"

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "${key}_line",
                        type = LayerType.LINE,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.any,
                                Filters.eqTypeLineString
                        ),
                        paint = Paint(
                                lineColor = options.color.from("CSTLN"),
                                lineWidth = 1.5f
                        )
                )
        )
    }
}