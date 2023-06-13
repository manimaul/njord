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

    override fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(areaColor)
        feature.lineColor(lineColor)
        feature.areaPattern(Sprite.AIRARE02)
        feature.pointSymbol(Sprite.AIRARE02)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillColor(areaColor),
        areaLayerWithFillPattern(),
        lineLayerWithColor(lineColor, width = 1f)
    )
}
