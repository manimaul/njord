package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catlnd
import io.madrona.njord.layers.attributehelpers.Catlnd.Companion.catlnd
import io.madrona.njord.model.*

class Lndrgn : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.POSGEN04)
        feature.catlnd().firstOrNull{ it == Catlnd.SWAMP || it == Catlnd.MARSH }?.let {
            feature.areaPattern(Sprite.MARSHES1P)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP,
        ),
        areaLayerWithFillPattern(),
    )
}
