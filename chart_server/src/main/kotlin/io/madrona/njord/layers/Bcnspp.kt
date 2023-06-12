package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catspm
import io.madrona.njord.layers.attributehelpers.Catspm.Companion.catspm
import io.madrona.njord.model.ChartFeature

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

    override fun preTileEncode(feature: ChartFeature) {
        when (feature.catspm()) {
            Catspm.NOTICE_MARK -> {
                feature.pointSymbol(Sprite.NOTBRD11)
            }
            Catspm.REFUGE_BEACON -> {
                feature.pointSymbol(Sprite.BCNSPP13)
            }
            Catspm.MARK_WITH_UNKNOWN_PURPOSE -> {
                feature.pointSymbol(Sprite.BCNDEF13)
            }
            else -> super.preTileEncode(feature)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
