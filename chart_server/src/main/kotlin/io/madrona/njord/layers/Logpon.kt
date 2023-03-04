package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Log pond
 *
 * Acronym: LOGPON
 *
 * Code: 80
 */
class Logpon : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.props["SY"] = "FLTHAZ02"
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
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
        Layer(
            id = "${key}_line_dash",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypeLineStringOrPolygon,
            paint = Paint(
                lineColor = colorFrom("CHBLK"),
                lineWidth = 1f,
                lineDashArray = listOf(3f, 4f),
            ),
        ),
    )

}
