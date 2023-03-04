package io.madrona.njord.layers

import io.madrona.njord.model.*

class Resare : LayerableTodo() {
    override fun preTileEncode(feature: ChartFeature) {

    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_line_dash",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypeLineStringOrPolygon,
            paint = Paint(
                lineColor = colorFrom("CHMGD"),
                lineWidth = 1f,
                lineDashArray = listOf(3f, 4f),
            ),
        ),
    )
}
