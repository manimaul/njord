package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Offshore production area
 *
 * Acronym: OSPARE
 *
 * Code: 88
 */
class Ospare : Layerable() {
    private val symbol = Sprite.CTYARE51
    private val color = Color.CHMGD

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(symbol)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = color, style = LineStyle.DashLine, width = 2f),
        pointLayerFromSymbol(
            symbol = Symbol.Sprite(symbol),
        ),
    )
}
