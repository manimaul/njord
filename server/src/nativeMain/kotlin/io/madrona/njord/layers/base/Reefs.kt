package io.madrona.njord.layers.base

import io.madrona.njord.layers.Layerable
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.layers.Symbol
import io.madrona.njord.layers.areaPattern
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Color
import io.madrona.njord.model.Sprite

/**
 * Natural Earth base layer: reefs (line geometry)
 */
class Reefs : Layerable("reefs") {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.areaPattern(Sprite.FOULAR01P)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(Symbol.Sprite(Sprite.FOULAR01P)),
            areaLayerWithFillPattern(Sprite.FOULAR01P),
            lineLayerWithColor(theme = options.theme, color = Color.CSTLN, width = 0.5f),
    )
}