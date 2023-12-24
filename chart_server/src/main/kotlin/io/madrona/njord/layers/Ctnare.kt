package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 *
 * Geometry Primitives: Point, Area
 *
 * Object: Caution area
 *
 * Acronym: CTNARE
 *
 * Code: 27
 */
class Ctnare : Layerable() {
    private val lineColor = Color.TRFCD

    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CHINFO06)

    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithPointSymbol(),
        lineLayerWithColor(
            theme = options.theme,
            color = lineColor,
            style = LineStyle.DashLine
        )
    )
}
