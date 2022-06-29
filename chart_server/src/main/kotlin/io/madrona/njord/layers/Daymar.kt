package io.madrona.njord.layers

import io.madrona.njord.model.*

class Daymar : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
            Layer(
                    id = "${key}_point",
                    type = LayerType.SYMBOL,
                    sourceLayer = key,
                    filter = listOf(Filters.any, Filters.eqTypePoint),
                    layout = Layout(
                            symbolPlacement = Placement.POINT,
                            iconImage = listOf("get", "SY"),
                            iconAnchor = Anchor.BOTTOM,
                            iconOffset = listOf(0f, -20f),
                            iconAllowOverlap = true,
                            iconKeepUpright = true,
                    )
            )
    )
}
