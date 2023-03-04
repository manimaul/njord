package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Anchorage area
 *
 * Acronym: ACHARE
 *
 * Code: 4
 */
class Achare : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.props["SY"] = "ACHARE02"
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_point_centroid",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = Filters.eqTypePointOrPolygon,
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = listOf("get", "SY"),
                iconAnchor = Anchor.BOTTOM,
                iconAllowOverlap = true,
                iconKeepUpright = false,
            )
        ),
        Layer(
            id = "${key}_line_dash",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                lineColor = colorFrom("CHMGF"),
                lineWidth = 2f,
                lineDashArray = listOf(3f, 4f),
            ),
        ),
    )
}
