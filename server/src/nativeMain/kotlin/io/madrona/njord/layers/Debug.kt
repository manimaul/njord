package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.model.*

class Debug : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
            Layer(
                    id = "debug-msg",
                    type = LayerType.SYMBOL,
                    sourceLayer = sourceLayer,
                    filter = listOf(
                            "any", Filters.eqTypePoint
                    ).json,
                    layout = Layout(
                            textFont = listOf(Font.ROBOTO_BOLD),
                            textAnchor = Anchor.CENTER,
                            textJustify = TextJustify.CENTER,
                            textField = listOf("get", "DMSG").json,
                            textAllowOverlap = true,
                            textIgnorePlacement = true,
                            textSize = 11f,
                            symbolPlacement = Placement.POINT,
                    ),
            ),
            Layer(
                    id = "debug-line",
                    type = LayerType.LINE,
                    sourceLayer = sourceLayer,
                    filter = listOf(
                            Filters.all,
                            Filters.eqTypeLineString
                    ).json,
                    paint = Paint(
                            lineColor = colorFrom(Color.CSTLN, options.theme).json,
                            lineWidth = 3f
                    )
            )
    )
}