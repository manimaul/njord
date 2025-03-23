package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Fairway
 *
 * Acronym: FAIRWY
 *
 * Code: 51
 */
class Fairwy : Layerable() {
    private val lineColor = Color.CHBLK
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            lineLayerWithColor(
                theme = options.theme,
                color = lineColor,
                width = 0.5f,
                style = LineStyle.CustomDash(10f, 5f)
            ),
        )
    }
}