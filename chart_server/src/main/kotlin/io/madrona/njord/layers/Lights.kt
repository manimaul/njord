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
            Color.Red -> feature.pointSymbol(Sprite.LIGHTS11)
            Color.Green -> feature.pointSymbol(Sprite.LIGHTS12)
            Color.Yellow -> feature.pointSymbol(Sprite.LIGHTS13)
            Color.White,
            Color.Amber,
            Color.Orange,
            Color.Magenta -> feature.pointSymbol(Sprite.LITDEF11)
            else -> feature.pointSymbol(Sprite.LITDEF11)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            anchor = Anchor.BOTTOM,
            iconAllowOverlap = true,
            iconKeepUpright = true,
            iconRotate = 135f,
        )
    )
}
