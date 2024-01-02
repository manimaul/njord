package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Land area
 *
 * Acronym: LNDARE
 *
 * Code: 71
 */
open class Lndare : Layerable() {
    open val areaColor = Color.LANDA

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.LNDARE01)
        feature.areaColor(areaColor)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            areaLayerWithFillColor(theme = options.theme, color = areaColor),
            lineLayerWithColor(theme = options.theme, color = Color.CSTLN, width = 2f),
            pointLayerFromSymbol(anchor = Anchor.CENTER, iconAllowOverlap = true, iconKeepUpright = false),
        )
    }
}
