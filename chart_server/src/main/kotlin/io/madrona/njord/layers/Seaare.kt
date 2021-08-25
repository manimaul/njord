package io.madrona.njord.layers

import io.madrona.njord.model.*

class Seaare : Layerable {
    override val key = "SEAARE"
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "${key}_fill",
                        paint = Paint(
                                fillColor = options.color.from("CHWHT")
                        ),
                        sourceLayer = "SEAARE",
                        type = LayerType.FILL
                ),
//                Layer(
//                        id = "${key}_point",
//                        type = LayerType.SYMBOL,
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