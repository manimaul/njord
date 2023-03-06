package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Restrn
import io.madrona.njord.layers.attributehelpers.restrn
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Cable area
 *
 * Acronym: CBLARE
 *
 * Code: 20
 */
class Cblare : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.props["SY"] = "CBLARE51"
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_line_dash",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                lineColor = colorFrom("CHMGD"),
                lineWidth = 2f,
                lineDashArray = listOf(3f, 2f),
            ),
        ),
        Layer(
            id = "${key}_area_symbol",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = listOf("get", "SY"),
                iconOffset = listOf(30f, 30f), // give room for resare
                iconAnchor = Anchor.CENTER,
                iconKeepUpright = false,
            )
        ),
    )
}
