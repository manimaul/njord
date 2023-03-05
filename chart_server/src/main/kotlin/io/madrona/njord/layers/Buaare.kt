package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Built-up area
 *
 * Acronym: BUAARE
 *
 * Code: 13
 */
class Buaare : Layerable() {
        override fun preTileEncode(feature: ChartFeature) {
                feature.props["SY"] = "BUAARE02"
                feature.props["AC"] = "LANDF"
        }

        override fun layers(options: LayerableOptions) = sequenceOf(
                Layer(
                        id = "${key}_fill",
                        type = LayerType.FILL,
                        sourceLayer = key,
                        filter = Filters.eqTypePolyGon,
                        paint = Paint(
                                fillColor = colorFrom("CHBRN")
                        ),
                ),
                Layer(
                        id = "${key}_line",
                        type = LayerType.LINE,
                        sourceLayer = key,
                        filter = Filters.eqTypeLineStringOrPolygon,
                        paint = Paint(
                                lineColor = colorFrom("CSTLN"),
                                lineWidth = 1f
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
                                iconAnchor = Anchor.BOTTOM,
                                iconAllowOverlap = true,
                                iconKeepUpright = false,
                        )
                ),
        )
}
