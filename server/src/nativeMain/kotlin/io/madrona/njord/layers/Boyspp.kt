package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catspm
import io.madrona.njord.layers.attributehelpers.Catspm.Companion.catspm
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

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
        feature.catspm().firstOrNull {
            when (it) {
                Catspm.LANBY_LARGE_AUTOMATIC_NAVIGATIONAL_BUOY -> {
                    feature.pointSymbol(Sprite.BOYSUP02)
                    true
                }
                Catspm.MARK_WITH_UNKNOWN_PURPOSE -> {
                    feature.pointSymbol(Sprite.BOYDEF03)
                    true
                }
                else -> false
            }
        } ?: boyshp(feature)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol()
    )
}

