package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.Color
import io.madrona.njord.geo.symbols.Color.Companion.colors
import io.madrona.njord.layers.attributehelpers.Bcnshp
import io.madrona.njord.layers.attributehelpers.Bcnshp.Companion.bcnshp
import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point
 *
 * Object: Beacon, lateral
 *
 * Acronym: BCNLAT
 *
 * Code: 7
 */
open class Bcnlat : Layerable() {

    override fun preTileEncode(feature: ChartFeature) {
        when (feature.bcnshp()) {
            Bcnshp.STAKE_POLE_PERCH_POST,
            Bcnshp.WHITY -> {
                when (feature.colors().firstOrNull()) {
                    Color.Red -> feature.pointSymbol(Sprite.BCNLAT21) //red tall beacon
                    Color.Green -> feature.pointSymbol(Sprite.BCNLAT22) //green tall beacon
                    Color.Amber,
                    Color.Yellow -> feature.pointSymbol(Sprite.BCNSPP21) //yellow tall beacon
                    else -> feature.pointSymbol(Sprite.BCNSAW21) //black tall beacon
                }
            }
            Bcnshp.BEACON_TOWER,
            Bcnshp.LATTICE_BEACON,
            Bcnshp.PILE_BEACON -> {
                when (feature.colors().firstOrNull()) {
                    Color.Red -> feature.pointSymbol(Sprite.BCNLAT15) //red short beacon
                    Color.Green -> feature.pointSymbol(Sprite.BCNLAT15) //green short beacon
                    Color.Amber,
                    Color.Yellow -> feature.pointSymbol(Sprite.BCNSPP13) //yellow short beacon
                    else -> feature.pointSymbol(Sprite.BCNSAW13) //black short beacon
                }
            }
            Bcnshp.CAIRN -> {
                feature.pointSymbol(Sprite.CAIRNS11)
            }
            Bcnshp.BUOYANT_BEACON -> {
                feature.pointSymbol(Sprite.BCNLAT21)
            }
            null -> feature.pointSymbol(Sprite.BCNDEF13)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
