package io.madrona.njord.layers

import io.madrona.njord.model.*

open class Lndare : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.props["SY"] = "LNDARE01"
        feature.props["AC"] = "LANDA"
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_fill",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = Filters.eqTypePolyGon,
                paint = Paint(
                    //fillColor = Filters.areaFillColor,
                    fillColor = colorFrom("LANDA")
                ),
            ),
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = Filters.eqTypeLineStringOrPolygon,
                paint = Paint(
                    lineColor = colorFrom("CSTLN"),
                    lineWidth = 2f
                )
            ),
            Layer(
                id = "${key}_point",
                type = LayerType.SYMBOL,
                sourceLayer = key,
                filter = Filters.eqTypePoint,
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
