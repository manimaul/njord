package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Recommended Traffic Lane Part
 *
 * Acronym: RCTLPT
 *
 * Code: 110
 */
class Rctlpt : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.props.floatValue("ORIENT")?.let {
            feature.pointSymbol(Sprite.RCTLPT52)
        } ?: feature.pointSymbol(Sprite.RTLDEF51)

    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            iconRotate = IconRot.Property("ORIENT"),
            iconRotationAlignment = IconRotationAlignment.MAP,
            iconAllowOverlap = false
        ),
        areaLayerWithPointSymbol(
            iconRotate = IconRot.Property("ORIENT"),
            iconRotationAlignment = IconRotationAlignment.MAP,
            iconAllowOverlap = false,
        ),
    )
}
