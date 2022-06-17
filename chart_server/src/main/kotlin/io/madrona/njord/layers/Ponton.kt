package io.madrona.njord.layers

import io.madrona.njord.model.*

class Ponton : Layerable {
    override val key = "PONTON"

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_area_fill",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = listOf(
                    Filters.all,
                    Filters.eqTypePolyGon
                ),
                paint = Paint(
                    fillColor = colorFrom("CHBRN")
                )
            ),
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = listOf(
                    Filters.any,
                    Filters.eqTypePolyGon,
                    Filters.eqTypeLineString,
                ),
                paint = Paint(
                    lineColor = colorFrom("CSTLN"),
                    lineWidth = 2f
                )
            )
        )
    }
}