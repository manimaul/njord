package io.madrona.njord.layers.base

import io.madrona.njord.layers.Layerable
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.layers.Symbol
import io.madrona.njord.layers.areaPattern
import io.madrona.njord.layers.pointSymbol
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Color
import io.madrona.njord.model.Layer
import io.madrona.njord.model.Sprite

/**
 * Natural Earth base layer: glaciated areas
 */
class GlaciatedAreas : Layerable("glaciated_areas") {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.ICEARE04P)
        feature.areaPattern(Sprite.ICEARE04P)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            pointLayerFromSymbol(Symbol.Sprite(Sprite.ICEARE04P)),
            areaLayerWithFillPattern(Sprite.ICEARE04P),
            lineLayerWithColor(theme = options.theme, color = Color.CSTLN, width = 0.5f),
        )
    }
}