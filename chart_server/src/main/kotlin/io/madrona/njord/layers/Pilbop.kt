package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Pilot boarding place
 *
 * Acronym: PILBOP
 *
 * Code: 91
 */
class Pilbop : Layerable() {
    private val lineColor = Color.TRFCF

    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.PILBOP02)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.DashLine),
        areaLayerWithSingleSymbol()
    )

}
