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

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CHINFO06)

    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP,
        ),
        areaLayerWithPointSymbol(
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP,
        ),
        lineLayerWithColor(
            theme = options.theme,
            color = lineColor,
            style = LineStyle.DashLine
        )
    )
}
