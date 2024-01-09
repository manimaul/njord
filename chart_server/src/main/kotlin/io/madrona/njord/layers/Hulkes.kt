package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Hulk
 *
 * Acronym: HULKES
 *
 * Code: 65
 */
class Hulkes : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.HULKES01)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            areaLayerWithFillColor(theme = options.theme, color = Color.CHBRN),
            lineLayerWithColor(theme = options.theme, color = Color.CSTLN, width = 2f),
            pointLayerFromSymbol(
                symbol = Sprite.HULKES01,
                anchor = Anchor.CENTER,
                iconAllowOverlap = true,
                iconKeepUpright = false,
                iconRotationAlignment = IconRotationAlignment.MAP,
            ),
        )
    }
}