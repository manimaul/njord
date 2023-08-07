package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Dumping ground
 *
 * Acronym: DMPGRD
 *
 * Code: 48
 */
class Dmpgrd : Layerable() {
    private val lineColor = Color.CHMGD

    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CHINFO07)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(lineColor, width = 1f, style = LineStyle.DashLine)
    )
}
