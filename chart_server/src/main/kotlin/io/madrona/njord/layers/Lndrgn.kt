package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catlnd
import io.madrona.njord.layers.attributehelpers.catlnd
import io.madrona.njord.model.*

class Lndrgn : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.props["SY"] = "POSGEN04"
        val catlnd = feature.props.catlnd()
        catlnd.firstOrNull{ it == Catlnd.SWAMP || it == Catlnd.MARSH }?.let {
            feature.props["AP"] = "MARSHES1"
        }
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
            id = "${key}_fill_pattern",
            type = LayerType.FILL,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                fillPattern = listOf("get", "AP")
            )
        ),
    )
}
