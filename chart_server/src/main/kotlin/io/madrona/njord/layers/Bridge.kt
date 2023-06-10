package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catbrg
import io.madrona.njord.layers.attributehelpers.Catbrg.Companion.catbrg
import io.madrona.njord.model.*


class Bridge : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        val categories = feature.catbrg()
        categories.firstOrNull{
            it == Catbrg.OPENING_BRIDGE
                    || it == Catbrg.SWING_BRIDGE
                    || it == Catbrg.LIFTING_BRIDGE
                    || it == Catbrg.BASCULE_BRIDGE
                    || it == Catbrg.DRAW_BRIDGE
                    || it == Catbrg.TRANSPORTER_BRIDGE
        }?.let {
            feature.pointSymbol(Sprite.BRIDGE01)
            feature.linePattern(Sprite.BRIDGE01)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(color = Color.CHGRD, width = 4f),
        pointLayerFromSymbol(iconKeepUpright = true),
        lineLayerWithPattern(),
    )
}
