package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.Colour
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.layers.attributehelpers.Boyshp
import io.madrona.njord.layers.attributehelpers.Boyshp.Companion.boyshp
import io.madrona.njord.layers.attributehelpers.Catlam
import io.madrona.njord.layers.attributehelpers.Catlam.Companion.catlam
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Buoy, lateral
 *
 * Acronym: BOYLAT
 *
 * Code: 17
 */
open class Boylat : Layerable() {
    fun halfTriangle(feature: ChartFeature) {
        when (feature.colors().firstOrNull()) {
            Colour.Red -> feature.pointSymbol(Sprite.BOYLAT14)
            Colour.Green -> feature.pointSymbol(Sprite.BOYLAT13)
            else -> feature.pointSymbol(Sprite.BOYSPP15)
        }
    }

    fun rhomboid(feature: ChartFeature) {
        when (feature.colors().firstOrNull()) {
            Colour.Red -> feature.pointSymbol(Sprite.BOYLAT24)
            Colour.Green -> feature.pointSymbol(Sprite.BOYLAT23)
            else -> feature.pointSymbol(Sprite.BOYSPP25)
        }
    }

    fun circle(feature: ChartFeature) {
        when (feature.colors().firstOrNull()) {
            Colour.Red -> feature.pointSymbol(Sprite.BOYSAW12)
            else -> feature.pointSymbol(Sprite.BOYSPP11)
        }
    }

    fun stake(feature: ChartFeature) {
        when (feature.colors().firstOrNull()) {
            Colour.Red -> feature.pointSymbol(Sprite.BCNLAT21) //red tall beacon
            Colour.Green -> feature.pointSymbol(Sprite.BCNLAT22) //green tall beacon
            Colour.Black -> feature.pointSymbol(Sprite.BCNSAW21) //black tall beacon
            else  -> feature.pointSymbol(Sprite.BCNSPP21) //yellow tall beacon
        }
    }

    fun boyshp(feature: ChartFeature) {
        when (feature.boyshp()) {
            Boyshp.CONICAL -> halfTriangle(feature)
            Boyshp.CAN -> rhomboid(feature)
            Boyshp.SPHERICAL -> circle(feature)
            Boyshp.PILLAR,
            Boyshp.SPAR -> stake(feature)
            Boyshp.BARREL,
            Boyshp.SUPERBUOY,
            Boyshp.ICEBUOY -> circle(feature)
            null -> {
                feature.pointSymbol(Sprite.BOYDEF03)
            }
        }
    }

    override fun preTileEncode(feature: ChartFeature) {
        when (feature.catlam()) {
            Catlam.PORT_HAND_LATERAL_MARK -> rhomboid(feature)
            Catlam.STARBOARD_HAND_LATERAL_MARK -> halfTriangle(feature)
            Catlam.PREFERRED_CHANNEL_TO_STARBOARD_LATERAL_MARK -> rhomboid(feature)
            Catlam.PREFERRED_CHANNEL_TO_PORT_LATERAL_MARK -> halfTriangle(feature)
            null -> boyshp(feature)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol()
    )
}
