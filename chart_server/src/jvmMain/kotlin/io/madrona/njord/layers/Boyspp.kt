package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catspm
import io.madrona.njord.layers.attributehelpers.Catspm.Companion.catspm
import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point
 *
 * Object: Buoy, special purpose/general
 *
 * Acronym: BOYSPP
 *
 * Code: 19
 */
class Boyspp : Boylat() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.catspm()) {
            Catspm.LANBY_LARGE_AUTOMATIC_NAVIGATIONAL_BUOY -> feature.pointSymbol(Sprite.BOYSUP02)
            Catspm.MARK_WITH_UNKNOWN_PURPOSE -> feature.pointSymbol(Sprite.BOYDEF03)
            else -> boyshp(feature)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol()
    )
}

