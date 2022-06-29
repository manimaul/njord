package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * /v1/app/control/symbols/LIGHTS
 */
class Lights : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_point",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = listOf(Filters.any, Filters.eqTypePoint),
            layout = Layout(
                iconImage = listOf("get", "SY"),
                iconKeepUpright = true,
                iconAnchor = Anchor.TOP_LEFT,
                iconAllowOverlap = true,
                symbolPlacement = Placement.POINT
            )
        )
    )
}