package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catlnd
import io.madrona.njord.layers.attributehelpers.Catlnd.Companion.catlnd
import io.madrona.njord.model.*

class Lndrgn : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.POSGEN04)
        feature.catlnd().firstOrNull{ it == Catlnd.SWAMP || it == Catlnd.MARSH }?.let {
            feature.areaPattern(Sprite.MARSHES1)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillPattern(),
    )
}
