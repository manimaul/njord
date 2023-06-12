package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catcam
import io.madrona.njord.layers.attributehelpers.Catcam.Companion.catcam
import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point
 *
 * Object: Buoy, cardinal
 *
 * Acronym: BOYCAR
 *
 * Code: 14
 */
class Boycar : Bcnlat() {

    override fun preTileEncode(feature: ChartFeature) {
        when (feature.catcam()) {
            Catcam.NORTH_CARDINAL_MARK -> feature.pointSymbol(Sprite.BOYCAR01)
            Catcam.EAST_CARDINAL_MARK -> feature.pointSymbol(Sprite.BOYCAR02)
            Catcam.SOUTH_CARDINAL_MARK -> feature.pointSymbol(Sprite.BOYCAR03)
            Catcam.WEST_CARDINAL_MARK -> feature.pointSymbol(Sprite.BOYCAR04)
            null -> feature.pointSymbol(Sprite.BOYDEF03)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
