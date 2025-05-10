package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Log pond
 *
 * Acronym: LOGPON
 *
 * Code: 80
 */
class Logpon : Layerable() {
    private val lineColor = Color.CHBLK

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.FLTHAZ02)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(
            theme = options.theme,
            color = lineColor,
            width = 1f,
            style = LineStyle.CustomDash(3f, 4f)
        ),
    )
}
