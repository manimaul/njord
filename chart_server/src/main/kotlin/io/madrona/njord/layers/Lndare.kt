package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * /v1/app/control/symbols/LNDARE
 */
class Lndare : Layerable {
    override val key = "LNDARE"

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_fill",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = listOf(
                    Filters.all,
                    Filters.eqTypePolyGon
                ),
                paint = Paint(
                    fillColor = colorFrom("LANDA")
                )
            ),
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = listOf(
                    Filters.any,
                    Filters.eqTypePolyGon
                ),
                paint = Paint(
                    lineColor = colorFrom("CSTLN"),
                    lineWidth = 1.5f
                )
            ),
            Layer(
                id = "${key}_point",
                type = LayerType.SYMBOL,
                sourceLayer = key,
                filter = listOf(Filters.any, Filters.eqTypePoint),
                layout = Layout(
                    symbolPlacement = Placement.POINT,
                    iconImage = listOf("get", "SY"),
                    iconAnchor = Anchor.CENTER,
                    iconAllowOverlap = true,
                    iconKeepUpright = false,
                )
            )
        )
    }
}