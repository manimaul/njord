package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Incineration area
 *
 * Acronym: ICNARE
 *
 * Code: 67
 *
 */
class Icnare : Layerable() {
    private val lineColor = Color.CHMGF
    private val symbol = Sprite.CHINFO07
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
        feature.areaPattern(symbol)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.DashLine, width = 1f),
        pointLayerFromSymbol(
            symbol = Symbol.Sprite(symbol),
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP,
        ),
        areaLayerWithSingleSymbol(symbol = symbol),
    )
}
