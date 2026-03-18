package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line
 *
 * Object: Water turbulence
 *
 * Acronym: WATTUR
 *
 * Code: 158
 */
class Wattur : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.WATTUR02)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(theme = options.theme, color = Color.DEPMD, width = 1f),
    )
}
