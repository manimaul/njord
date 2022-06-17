package io.madrona.njord.layers

import io.madrona.njord.model.*

class Debug : Layerable {
    override val key = "DEBUG"

    override fun layers(options: LayerableOptions) = sequenceOf(
            Layer(
                    id = "debug-msg",
                    type = LayerType.SYMBOL,
                    sourceLayer = key,
                    filter = listOf(
                            "any", Filters.eqTypePoint
                    ),
                    layout = Layout(
                            textFont = listOf(Font.ROBOTO_BOLD),
                            textAnchor = Anchor.CENTER,
                            textJustify = Anchor.CENTER,
                            textField = listOf("get", "DMSG"),
                            textAllowOverlap = true,
                            textIgnorePlacement = true,
                            textSize = 11f,
                            symbolPlacement = Placement.POINT,
                    ),
            ),
            Layer(
                    id = "debug-line",
                    type = LayerType.LINE,
                    sourceLayer = key,
                    filter = listOf(
                            Filters.all,
                            Filters.eqTypeLineString
                    ),
                    paint = Paint(
                            lineColor = colorFrom("CSTLN"),
                            lineWidth = 3f
                    )
            )
    )
}