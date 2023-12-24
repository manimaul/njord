package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Convis
import io.madrona.njord.layers.attributehelpers.Convis.Companion.convis
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Fortified structure
 *
 * Acronym: FORSTC
 *
 * Code: 59
 */
class Forstc : Layerable() {
    private val lineColors = setOf(Color.LANDF, Color.CHBLK)
    override fun preTileEncode(feature: ChartFeature) {
        when (feature.convis()) {
            Convis.VISUAL_CONSPICUOUS -> {
                feature.pointSymbol(Sprite.FORSTC11)
                feature.lineColor(Color.CHBLK)
                feature.areaColor(Color.CHBRN)
            }
            Convis.NOT_VISUAL_CONSPICUOUS,
            null -> {
                feature.pointSymbol(Sprite.FORSTC11)
                feature.lineColor(Color.LANDF)
                feature.areaColor(Color.CHBRN)
            }
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillColor(color = Color.CHBRN, theme = options.theme),
        lineLayerWithColor(options = lineColors, theme = options.theme),
    )
}
