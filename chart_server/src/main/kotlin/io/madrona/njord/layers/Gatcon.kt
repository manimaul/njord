package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catgat
import io.madrona.njord.layers.attributehelpers.Catgat.Companion.catgat
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Gate
 *
 * Acronym: GATCON
 *
 * Code: 61
 */
class Gatcon : Layerable() {
    private val areaColor = Color.CHBRN
    private val lineColor = Color.CSTLN

    override fun preTileEncode(feature: ChartFeature) {
        feature.catgat()?.let {
            when(it) {
                Catgat.FLOOD_BARRAGE_GATE,
                Catgat.CAISSON ->  feature.pointSymbol(Sprite.GATCON04)
                Catgat.LOCK_GATE -> feature.pointSymbol(Sprite.GATCON03)
                Catgat.DYKE_GATE -> Unit
            }
        }
        feature.areaColor(areaColor)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillColor(theme = options.theme, color = areaColor),
        lineLayerWithColor(theme = options.theme, color = lineColor, width = 2f)
    )
}
