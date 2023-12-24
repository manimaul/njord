package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Pylon/bridge support
 *
 * Acronym: PYLONS
 *
 * Code: 98
 */
class Pylons : Layerable() {
    private val areaColor = Color.CHBRN
    private val lineColor = Color.CSTLN

    override fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(areaColor)
        feature.lineColor(lineColor)
        feature.pointSymbol(Sprite.POSGEN03)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillColor(theme = options.theme, color = areaColor),
        lineLayerWithColor(theme = options.theme, color = lineColor, width = 2f)
    )
}
