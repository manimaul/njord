package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Land area
 *
 * Acronym: LNDARE
 *
 * Code: 71
 */
open class Lndare : Layerable() {
    open val areaColor = "LANDA"

    override fun preTileEncode(feature: ChartFeature) {
        feature.props["SY"] = "LNDARE01"
        feature.props["AC"] = areaColor
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
                    fillColor = colorFrom(areaColor)
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
