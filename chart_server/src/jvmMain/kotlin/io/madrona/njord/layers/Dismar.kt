package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * https://openenc.com/control/symbols/DISMAR
 *
 * Geometry Primitives: Point
 *
 * Object: Distance mark
 *
 * Acronym: DISMAR
 *
 * Code: 44
 */
class Dismar : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.DISMAR06)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP,
        ),
    )
}
