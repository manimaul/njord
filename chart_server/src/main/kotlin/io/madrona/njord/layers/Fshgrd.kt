package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Fishing ground
 *
 * Acronym: FSHGRD
 *
 * Code: 56
 */
class Fshgrd : Layerable() {
    private val lineColor = Color.CHMGD
    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.FSHGRD01)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = lineColor),
        areaLayerWithSingleSymbol()
    )
}
