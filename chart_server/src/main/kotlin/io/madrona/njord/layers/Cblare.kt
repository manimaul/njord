package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Cable area
 *
 * Acronym: CBLARE
 *
 * Code: 20
 */
class Cblare : Layerable() {
    private val lineColor = Color.CHMGD
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CBLARE51)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            theme = options.theme,
            color = lineColor,
            style = LineStyle.CustomDash(3f, 2f)
        ),
        areaLayerWithSingleSymbol(
            iconOffset = listOf(30f, 30f), // give room for resare
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP,
        )
    )
}
