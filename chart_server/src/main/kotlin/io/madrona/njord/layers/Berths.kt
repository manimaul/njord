package io.madrona.njord.layers

import io.madrona.njord.model.*

class Berths : SymbolLayerable() {
    override val key = "BERTHS"

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
                            iconAllowOverlap = true,
                            iconKeepUpright = true,
                    )
            )
    )
}