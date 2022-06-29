package io.madrona.njord.layers

import io.madrona.njord.model.*

class Slcons : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "${key}_line",
                        type = LayerType.LINE,
                        sourceLayer = key,
                        filter = listOf(Filters.all),
                        paint = Paint(
                                lineColor = colorFrom("CSTLN"),
                                lineWidth = 1f
                        )
                )
        )
    }
}