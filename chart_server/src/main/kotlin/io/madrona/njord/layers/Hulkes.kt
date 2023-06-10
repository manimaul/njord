package io.madrona.njord.layers

import io.madrona.njord.model.*

class Hulkes : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.HULKES01)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            areaLayerWithFillColor(Color.CHBRN),
            lineLayerWithColor(color = Color.CSTLN, width = 2f),
            pointLayerFromSymbol(
                symbol = Sprite.HULKES01,
                anchor = Anchor.CENTER,
                iconAllowOverlap = true,
                iconKeepUpright = false
            ),
        )
    }
}