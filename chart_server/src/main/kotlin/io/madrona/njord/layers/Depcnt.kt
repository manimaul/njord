package io.madrona.njord.layers

import io.madrona.njord.model.*

class Depcnt : Layerable {
    override val key = "DEPCNT"
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "depth_contour",
                        type = LayerType.LINE,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypeLineString
                        ),
                        paint = Paint(
                                lineColor = options.color.from("CSTLN"),
                                lineWidth = 0.5f
                        )
                )
        )
    }
}