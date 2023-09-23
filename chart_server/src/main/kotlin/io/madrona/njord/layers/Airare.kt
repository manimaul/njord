package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Airport / airfield
 *
 * Acronym: AIRARE
 *
 * Code: 2
 */
class Airare : Layerable() {
    private val areaColor = Color.LANDA
    private val lineColor = Color.LANDF
    private val symbol = Sprite.AIRARE02

    override fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(areaColor)
        feature.lineColor(lineColor)
        feature.pointSymbol(symbol)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillColor(areaColor),
        areaLayerWithPointSymbol(symbol),
        lineLayerWithColor(lineColor, width = 1f)
    )
}
