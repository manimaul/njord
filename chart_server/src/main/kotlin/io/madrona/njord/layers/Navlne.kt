package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * http://localhost:3000/control/symbols/NAVLNE/INFORM
 */
class Navlne : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = Filters.eqTypeLineString,
                paint = Paint(
                    lineColor = colorFrom("CHBLK"),
                    lineWidth = 0.5f,
                    lineDashArray = listOf(10f, 5f)
                )
            ),
            Layer(
                id = "${key}_label",
                type = LayerType.SYMBOL,
                sourceLayer = key,
                filter = Filters.eqTypeLineString,
                layout = Layout(
                    textFont = listOf(Font.ROBOTO_BOLD),
                    textJustify = Anchor.CENTER,
                    textField = listOf("get", "INFORM"),
                    textSize = 14f,
                    symbolPlacement = Placement.LINE,
                ),
                paint = Paint(
                    textColor = colorFrom("CHBLK"),
                    textHaloColor = colorFrom("CHWHT"),
                    textHaloWidth = 2.5f
                )
            )
        )
    }
}
