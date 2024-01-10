package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.layers.attributehelpers.Trafic
import io.madrona.njord.layers.attributehelpers.Trafic.Companion.trafic
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Deep water route part
 *
 * Acronym: DWRTPT
 *
 * Code: 41
 */
class Dwrtpt : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.props.floatValue("ORIENT")?.let {
            when (feature.trafic()) {
                Trafic.INBOUND,
                Trafic.OUTBOUND,
                Trafic.ONE_WAY -> feature.pointSymbol(Sprite.TSSLPT51)
                Trafic.TWO_WAY -> feature.pointSymbol(Sprite.DWRUTE51)
                null -> {}
            }
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithPointSymbol(
            iconRotate = IconRot.Property("ORIENT"),
            iconRotationAlignment = IconRotationAlignment.MAP,
            iconAllowOverlap = false,
        ),
    )
}
