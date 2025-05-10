package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catspm
import io.madrona.njord.layers.attributehelpers.Catspm.Companion.catspm
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Beacon, special purpose/general
 *
 * Acronym: BCNSPP
 *
 * Code: 9
 */
class Bcnspp : Bcnlat() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.catspm().firstOrNull {
            when (it) {
                Catspm.NOTICE_MARK -> {
                    feature.pointSymbol(Sprite.NOTBRD11)
                    true
                }
                Catspm.REFUGE_BEACON -> {
                    feature.pointSymbol(Sprite.BCNSPP13)
                    true
                }
                Catspm.MARK_WITH_UNKNOWN_PURPOSE -> {
                    feature.pointSymbol(Sprite.BCNDEF13)
                    true
                }
                else -> false
            }
        } ?: super.preTileEncode(feature)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
