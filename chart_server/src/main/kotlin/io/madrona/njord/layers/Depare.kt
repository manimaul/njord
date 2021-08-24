package io.madrona.njord.layers

import io.madrona.njord.model.*

object Depare {
    fun layers(color: StyleColor): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "DEPARE_fill_2",
                        paint = Paint(
                                fillColor = color.from("DEPMD")
                        ),
                        source = "src_senc",
                        sourceLayer = "DEPARE",
                        type = LayerType.FILL,
                        filter = listOf(
                                "all",
                                listOf("==", "\$type", "Polygon"),
                                listOf("<=", "DRVAL1", 9)
                        )
                )
        )
    }
}