package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catcam
import io.madrona.njord.layers.attributehelpers.Catcam.Companion.catcam
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Beacon, cardinal
 *
 * Acronym: BCNCAR
 *
 * Code: 5
 */
class Bcncar : Layerable() {

    override fun preTileEncode(feature: ChartFeature) {
        when (feature.catcam()) {
            Catcam.NORTH_CARDINAL_MARK -> feature.pointSymbol(Sprite.BCNCAR01)
            Catcam.EAST_CARDINAL_MARK -> feature.pointSymbol(Sprite.BCNCAR02)
            Catcam.SOUTH_CARDINAL_MARK -> feature.pointSymbol(Sprite.BCNCAR03)
            Catcam.WEST_CARDINAL_MARK -> feature.pointSymbol(Sprite.BCNCAR04)
            null -> feature.pointSymbol(Sprite.BCNDEF13)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
