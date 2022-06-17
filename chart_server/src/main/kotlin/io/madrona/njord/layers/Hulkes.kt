package io.madrona.njord.layers

import io.madrona.njord.model.*

class Hulkes : Layerable {
    override val key = "HULKES"

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_area_fill",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = listOf(
                    Filters.all,
                    Filters.eqTypePolyGon
                ),
                paint = Paint(
                    fillColor = colorFrom("CHBRN")
                )
            ),
            Layer(
                id = "${key}_area_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = listOf(
                    Filters.any,
                    Filters.eqTypePolyGon,
                    Filters.eqTypeLineString
                ),
                paint = Paint(
                    lineColor = colorFrom("CSTLN"),
                    lineWidth = 2f
                )
            ),
            Layer(
                id = "${key}_point",
                type = LayerType.SYMBOL,
                sourceLayer = key,
                filter = listOf(
                    Filters.any,
                    Filters.eqTypePoint
                ),
                layout = Layout(
                    symbolPlacement = Placement.POINT,
                    iconImage = "HULKES01",
                    iconAnchor = Anchor.CENTER,
                    iconAllowOverlap = true,
                    iconKeepUpright = false,
                )
            ),
        )
    }
}