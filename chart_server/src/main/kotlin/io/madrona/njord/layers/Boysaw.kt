package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point
 *
 * Object: Buoy, safe water
 *
 * Acronym: BOYSAW
 *
 * Code: 18
 */
class Boysaw : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.colors().firstOrNull()) {
            Colour.Red -> feature.pointSymbol(Sprite.BOYSAW12)
            else -> feature.pointSymbol(Sprite.BOYSPP11)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol()
    )
}
