package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.intValueSet
import io.madrona.njord.model.*


class Bridge : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        /* https://s57dev.mxmariner.com/control/symbols/BRIDGE/CATBRG
        List
        1	fixed bridge
        2	opening bridge
        3	swing bridge
        4	lifting bridge
        5	bascule bridge
        6	pontoon bridge
        7	draw bridge
        8	transporter bridge
        9	footbridge
        10	viaduct
        11	aqueduct
        12	suspension bridge
         */
        val categories = feature.props.intValueSet("CATBRG")
        arrayOf(2,3,4,5,7,8).forEach {
            if  (categories.contains(it)) {
                feature.props["SY"] = "BRIDGE01"
                return
            }
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
                symbolPlacement = Placement.LINE_CENTER,
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
