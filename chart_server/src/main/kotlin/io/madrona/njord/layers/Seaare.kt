package io.madrona.njord.layers

import io.madrona.njord.model.*

object Seaare {
    fun layers(color: StyleColor): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "SEAARE_fill",
                        paint = Paint(
                                fillColor = ColorLibrary.colorMap.legendFrom(color)["CHWHT"]
                        ),
                        source = "src_senc",
                        sourceLayer = "SEAARE",
                        type = LayerType.FILL
                ),
//                Layer(
//                        id = "SEAARE_point",
//                        type = LayerType.SYMBOL,
//                        source = "src_senc",
//                        sourceLayer = "SEAARE",
//                        filter = listOf("any", listOf(">", "CATSEA", 0)),
//                        layout = Layout(
//                                textFont = listOf(Font.ROBOTO_BOLD),
//                                textAnchor = Anchor.CENTER,
//                                textJustify = Anchor.CENTER,
//                                textField = listOf("get", "OBJNAM"),
//                                textAllowOverlap = false,
//                                textIgnorePlacement = false,
//                                textMaxWidthEms = 9F,
//                                textSize = 12F,
//                                textPadding = 6F,
//                                symbolPlacement = Placement.POINT
//                        ),
//                        paint = Paint(
//                                textColor = ColorLibrary.black,
//                                textHaloColor = ColorLibrary.white,
//                                textHaloWidth = 1.5F
//
//                        )
//                )
        )
    }
}