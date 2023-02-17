package io.madrona.njord.layers

import io.madrona.njord.model.*

class Lndare : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.props["SY"] = "LNDARE01"
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_fill",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = Filters.eqTypePolyGon,
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
                    Filters.eqTypePolyGon,
                    Filters.eqTypeLineString,
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
