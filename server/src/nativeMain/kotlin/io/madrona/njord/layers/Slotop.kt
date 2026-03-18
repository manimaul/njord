package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Slope topline
 *
 * Acronym: SLOTOP
 *
 * Code: 127
 */
class Slotop : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.props.intValue("CONVIS")) {
            1 -> feature.pointSymbol(Sprite.HILTOP11)
            else -> feature.pointSymbol(Sprite.HILTOP01)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(theme = options.theme, color = Color.CHBLK, width = 1f),
    )
}
