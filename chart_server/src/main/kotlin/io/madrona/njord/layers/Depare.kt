package io.madrona.njord.layers

import io.madrona.njord.model.*

class Depare : Layerable {
    override val key = "DEPARE"

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "${key}_fill_2",
                        paint = Paint(
                                fillColor = options.color.from("DEPMD")
                        ),
                        sourceLayer = key,
                        type = LayerType.FILL,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                listOf("<=", "DRVAL1", 9)
                        )
                ),
                Layer(
                        id = "${key}_fill_1",
                        paint = Paint(
                                fillColor = options.color.from("DEPVS")
                        ),
                        type = LayerType.FILL,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                listOf("<=", "DRVAL1", 3.0)
                        )
                ),
                Layer(
                        id = "${key}_fill_0",
                        paint = Paint(
                                fillColor = options.color.from("DEPIT")
                        ),
                        type = LayerType.FILL,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                listOf("<", "DRVAL1", 0.0),
                                listOf("<=", "DRVAL2", 0.0),
                        )
                ),
                Layer(
                        id = "${key}_line",
                        paint = Paint(
                                lineColor = options.color.from("CSTLN"),
                                lineWidth = 0.5f
                        ),
                        type = LayerType.LINE,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                Filters.eqTypeLineString,
                        )
                )
        )
    }
}