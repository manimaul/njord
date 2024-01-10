package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Traffic Separation Scheme Lane part
 *
 * Acronym: TSSLPT
 *
 * Code: 148
 */
class Tsslpt : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.props.floatValue("ORIENT")?.let {
            feature.pointSymbol(Sprite.RCTLPT52)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithPointSymbol(
            iconRotate = IconRot.Property("ORIENT"),
            iconRotationAlignment = IconRotationAlignment.MAP,
            iconAllowOverlap = false
        ),
    )
}
