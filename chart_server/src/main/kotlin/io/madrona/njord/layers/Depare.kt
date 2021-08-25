package io.madrona.njord.layers

import io.madrona.njord.model.*

class Depare : Layerable {
    override val key = "DEPARE"

    override fun layers(color: StyleColor): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "${key}_fill_2",
                        paint = Paint(
                                fillColor = color.from("DEPMD")
                        ),
                        source = "src_senc",
                        sourceLayer = key,
                        type = LayerType.FILL,
                        filter = listOf(
                                "all",
                                listOf("==", "\$type", "Polygon"),
                                listOf("<=", "DRVAL1", 9)
                        )
                ),
                Layer(
                        id = "${key}_fill_1",
                        paint = Paint(
                                fillColor = color.from("DEPVS")
                        ),
                        type = LayerType.FILL,
                        source = "src_senc",
                        sourceLayer = key,
                        filter = listOf(
                                "all",
                                listOf("==", "\$type", "Polygon"),
                                listOf("<=", "DRVAL1", 3.0)
                        )
                ),
                Layer(
                        id = "${key}_fill_0",
                        paint = Paint(
                                fillColor = color.from("DEPIT")
                        ),
                        type = LayerType.FILL,
                        source = "src_senc",
                        sourceLayer = key,
                        filter = listOf(
                                "all",
                                listOf("==", "\$type", "Polygon"),
                                listOf("<", "DRVAL1", 0.0),
                                listOf("<=", "DRVAL2", 0.0),
                        )
                ),
                Layer(
                        id = "${key}_line",
                        paint = Paint(
                                lineColor = color.from("CSTLN"),
                                lineWidth = 0.5f
                        ),
                        type = LayerType.LINE,
                        source = "src_senc",
                        sourceLayer = key,
                        filter = listOf(
                                "all",
                                listOf("==", "\$type", "Polygon"),
                                listOf("==", "\$type", "LineString"),
                        )
                )
        )
    }
}