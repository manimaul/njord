package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Restrn
import io.madrona.njord.layers.attributehelpers.restrn
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Restricted area
 *
 * Acronym: RESARE
 *
 * Code: 112
 */
class Resare : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.props.restrn().also { restrictions ->
            restrictions.firstOrNull {
                it == Restrn.ENTRY_RESTRICTED
                        || it == Restrn.ENTRY_PROHIBITED
            }?.let {
                feature.props["SY"] = "ENTRES51"
            }
            restrictions.firstOrNull {
                it == Restrn.ANCHORING_PROHIBITED
                        || it == Restrn.ANCHORING_RESTRICTED
            }?.let {
                feature.props["SY"] = "ACHRES51"
            }
        }
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
                iconAnchor = Anchor.CENTER,
                iconKeepUpright = false,
            )
        ),
    )
}
