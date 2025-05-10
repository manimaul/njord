package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Unsurveyed area
 *
 * Acronym: UNSARE
 *
 * Code: 154
 */
class Unsare : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.areaColor(Color.NODTA)
        feature.areaPattern(Sprite.NODATA03P)
        feature.lineColor(Color.CHGRD)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(
            color = Color.NODTA,
            theme = options.theme
        ),
        lineLayerWithColor(
            color = Color.CHGRD,
            theme = options.theme
        ),
        areaLayerWithFillPattern(
            symbol = Sprite.NODATA03P
        ),
    )
}
