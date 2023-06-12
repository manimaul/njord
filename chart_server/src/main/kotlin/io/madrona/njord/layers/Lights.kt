package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.Color
import io.madrona.njord.geo.symbols.Color.Companion.colors
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Light
 *
 * Acronym: LIGHTS
 *
 * Code: 75
 */
class Lights : Layerable() {

    override fun preTileEncode(feature: ChartFeature) {
        when(feature.colors().firstOrNull()) {
            Color.White -> feature.pointSymbol(Sprite.LIGHTDEF1)
            Color.Red -> feature.pointSymbol(Sprite.LIGHTDEF3)
            Color.Green -> feature.pointSymbol(Sprite.LIGHTDEF4)
            Color.Yellow -> feature.pointSymbol(Sprite.LIGHTDEF6)
            Color.Amber,
            Color.Orange -> feature.pointSymbol(Sprite.LIGHTDEF11)
            Color.Magenta -> feature.pointSymbol(Sprite.LIGHTDEF12)
            else -> feature.pointSymbol(Sprite.LIGHTDEF6)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
//            iconOffset = listOf(10f, 0f),
            anchor = Anchor.TOP_LEFT,
            iconAllowOverlap = true,
            iconKeepUpright = true,
        )
    )
}
