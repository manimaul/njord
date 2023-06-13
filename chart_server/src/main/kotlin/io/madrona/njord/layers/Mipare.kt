package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Military practice area
 *
 * Acronym: MIPARE
 *
 * Code: 83
 */
class Mipare : Layerable() {
    private val lineColor = Color.CHMGD

    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CHINFO06)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithPointSymbol(),
        lineLayerWithColor(color = lineColor, style = LineStyle.DashLine)
    )
}
