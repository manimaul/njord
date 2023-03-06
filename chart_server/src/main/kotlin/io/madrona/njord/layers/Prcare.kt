package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Precautionary area
 *
 * Acronym: PRCARE
 *
 * Code: 96
 */
class Prcare : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.props["SY"] = "PRCARE12"
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_line_dash",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                lineColor = colorFrom("TRFCF"),
                lineWidth = 2f,
                lineDashArray = listOf(3f, 2f),
            ),
        ), Layer(
            id = "${key}_area_symbol",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = Filters.eqTypePointOrPolygon,
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = listOf("get", "SY"),
                iconAnchor = Anchor.CENTER,
                iconOffset = listOf(30f, 30f), // give room for lights / buoys
                iconKeepUpright = false,
            )
        )
    )
}
