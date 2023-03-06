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
    override fun preTileEncode(feature: ChartFeature) {
        feature.props.floatValue("ORIENT")?.let {
            feature.props["SY"] = "RCTLPT52"
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_area_symbol",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = Filters.eqTypePointOrPolygon,
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = listOf("get", "SY"),
                iconRotate = listOf("get", "ORIENT"),
                iconRotationAlignment = IconRotationAlignment.MAP,
                iconAnchor = Anchor.CENTER,
                iconKeepUpright = false,
            )
        )
    )
}
