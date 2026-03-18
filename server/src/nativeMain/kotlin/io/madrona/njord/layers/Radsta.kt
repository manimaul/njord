package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Radar station
 *
 * Acronym: RADSTA
 *
 * Code: 104
 */
class Radsta : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.props.intValue("CATRAS")) {
            2 -> feature.pointSymbol(Sprite.RDOSTA02)
            else -> feature.pointSymbol(Sprite.POSGEN01)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
