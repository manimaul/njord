package io.madrona.njord.layers

import io.madrona.njord.model.*

class Depare : Layerable {
    override val key = "DEPARE"

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "${key}_fill_2",
                        paint = Paint(
                                fillColor = colorFrom("DEPMD")
                        ),
                        sourceLayer = key,
                        type = LayerType.FILL,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                listOf(Filters.gtEq, "DRVAL1", 9)
                        )
                ),
                Layer(
                        id = "${key}_fill_1",
                        paint = Paint(
                                fillColor = colorFrom("DEPVS")
                        ),
                        type = LayerType.FILL,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                listOf(Filters.gtEq, "DRVAL1", 3.0)
                        )
                ),
                Layer(
                        id = "${key}_fill_0",
                        paint = Paint(
                                fillColor = colorFrom("DEPIT")
                        ),
                        type = LayerType.FILL,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                listOf(Filters.gt, "DRVAL1", 0.0),
                                listOf(Filters.gtEq, "DRVAL2", 0.0),
                        )
                ),
                Layer(
                        id = "${key}_line",
                        paint = Paint(
                                lineColor = colorFrom("CHGRD"),
                                lineWidth = 0.5f
                        ),
                        type = LayerType.LINE,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.any,
                                Filters.eqTypePolyGon,
                                Filters.eqTypeLineString,
                        )
                )
        )
    }
}