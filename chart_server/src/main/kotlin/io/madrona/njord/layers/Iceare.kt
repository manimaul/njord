package io.madrona.njord.layers

import io.madrona.njord.model.*

class Iceare : Layerable {
    override val key = "ICEARE"

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "${key}_fill",
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
                                Filters.eqTypeLineString
                        ),
                        paint = Paint(
                                lineColor = colorFrom("CSTLN"),
                                lineWidth = 1.5f
                        )
                )
        )
    }
}