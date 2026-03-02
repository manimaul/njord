package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Spoil area
 *
 * Acronym: SPLARE
 *
 * Code: 131
 */
class Splare : Layerable() {
    private val lineColor = Color.CHMGD

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CHINFO07)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithPointSymbol(anchor = Anchor.CENTER, iconRotationAlignment = IconRotationAlignment.MAP),
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.DashLine),
    )
}
