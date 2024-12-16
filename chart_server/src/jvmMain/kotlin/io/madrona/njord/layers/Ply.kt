package io.madrona.njord.layers

import io.madrona.njord.model.*

class Ply : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "${key}_line",
                        type = LayerType.LINE,
                        sourceLayer = sourceLayer,
                        filter = listOf(
                                Filters.any,
                                Filters.eqTypeLineString
                        ),
                        paint = Paint(
                                lineColor = colorFrom(Color.CHRED, options.theme),
                                lineWidth = 2f
                        )
                )
        )
    }
}