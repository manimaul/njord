package io.madrona.njord.layers

import io.madrona.njord.model.*

class Hulkes : Layerable {
    override val key = "HULKES"

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
                                fillColor = "#B7911F"
                        )
                ),
                Layer(
                        id = "${key}_line",
                        type = LayerType.LINE,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
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