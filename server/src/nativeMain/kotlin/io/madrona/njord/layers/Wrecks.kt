package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Wreck
 *
 * Acronym: WRECKS
 *
 * Code: 159
 */
class Wrecks : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.FOULGND1)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        areaLayerWithFillColor(theme = options.theme, color = Color.NODTA),
    )
}
