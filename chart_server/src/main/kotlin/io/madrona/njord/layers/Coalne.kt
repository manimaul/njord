package io.madrona.njord.layers

import io.madrona.njord.model.*

class Coalne : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(Color.CSTLN)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            lineLayerWithColor(
                theme = options.theme,
                color = Color.CSTLN,
                width = 1f,
                style = LineStyle.CustomDash(5f, 5f),
            )
        )
    }
}