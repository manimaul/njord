package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
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
            Colour.Red -> feature.pointSymbol(Sprite.LIGHTS11)
            Colour.Green -> feature.pointSymbol(Sprite.LIGHTS12)
            Colour.Yellow -> feature.pointSymbol(Sprite.LIGHTS13)
            Colour.White,
            Colour.Amber,
            Colour.Orange,
            Colour.Magenta -> feature.pointSymbol(Sprite.LITDEF11)
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
