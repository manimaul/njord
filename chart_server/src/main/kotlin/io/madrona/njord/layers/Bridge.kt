package io.madrona.njord.layers

import io.madrona.njord.model.*


class Bridge : SymbolLayerable() {

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
