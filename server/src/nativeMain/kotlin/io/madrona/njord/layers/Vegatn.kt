package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Vegetation
 *
 * Acronym: VEGATN
 *
 * Code: 153
 */
class Vegatn : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.props.intValue("CATVEG")) {
            7, 21 -> feature.pointSymbol(Sprite.VEGATN04P)
            else -> feature.pointSymbol(Sprite.VEGATN03P)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillColor(theme = options.theme, color = Color.CHBRN),
    )
}
