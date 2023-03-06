package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catbrg
import io.madrona.njord.layers.attributehelpers.catbrg
import io.madrona.njord.model.*


class Bridge : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        val categories = feature.props.catbrg()
        categories.firstOrNull{
            it == Catbrg.OPENING_BRIDGE
                    || it == Catbrg.SWING_BRIDGE
                    || it == Catbrg.LIFTING_BRIDGE
                    || it == Catbrg.BASCULE_BRIDGE
                    || it == Catbrg.DRAW_BRIDGE
                    || it == Catbrg.TRANSPORTER_BRIDGE
        }?.let {
            feature.props["SY"] = "BRIDGE01"
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_line_bridge",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = listOf(
                Filters.any,
                Filters.eqTypeLineString,
                Filters.eqTypePolyGon,
            ),
            paint = Paint(
                lineColor = colorFrom("CHGRD"),
                lineWidth = 4f,
            )
        ),
        Layer(
            id = "${key}_symbol_opening_bridge",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = Filters.eqTypeLineStringOrPolygon,
            layout = Layout(
                symbolPlacement = Placement.LINE,
                iconImage = listOf("get", "SY"),
                iconKeepUpright = true,
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
                iconAnchor = Anchor.BOTTOM,
                iconKeepUpright = true,
            )
        ),
    )
}
