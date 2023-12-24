package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Cargo transshipment area
 *
 * Acronym: CTSARE
 *
 * Code: 25
 */
class Ctsare : Layerable() {
    private val lineColor = Color.CHMGF

    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CHINFO07)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(
            theme = options.theme,
            color = lineColor,
            style = LineStyle.DashLine
        )
    )
}
