package io.madrona.njord.layers

import io.madrona.njord.model.*

class Fogsig : Layerable() {
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
                            iconOffset = listOf(-10f, 10f),
                            iconAllowOverlap = true,
                            iconKeepUpright = true,
                    )
            )
    )
}
