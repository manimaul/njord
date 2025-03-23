package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.layers.attributehelpers.Bcnshp
import io.madrona.njord.layers.attributehelpers.Bcnshp.Companion.bcnshp
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

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

    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.bcnshp()) {
            Bcnshp.STAKE_POLE_PERCH_POST,
            Bcnshp.WHITY -> {
                when (feature.colors().firstOrNull()) {
                    Colour.Red -> feature.pointSymbol(Sprite.BCNLAT21) //red tall beacon
                    Colour.Green -> feature.pointSymbol(Sprite.BCNLAT22) //green tall beacon
                    Colour.Black -> feature.pointSymbol(Sprite.BCNSAW21) //black tall beacon
                    else  -> feature.pointSymbol(Sprite.BCNSPP21) //yellow tall beacon
                }
            }
            Bcnshp.BEACON_TOWER,
            Bcnshp.LATTICE_BEACON,
            Bcnshp.PILE_BEACON -> {
                when (feature.colors().firstOrNull()) {
                    Colour.Red -> feature.pointSymbol(Sprite.BCNLAT15) //red short beacon
                    Colour.Green -> feature.pointSymbol(Sprite.BCNLAT16) //green short beacon
                    Colour.Black -> feature.pointSymbol(Sprite.BCNSAW13) //black short beacon
                    else -> feature.pointSymbol(Sprite.BCNSPP13) //yellow short beacon
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
