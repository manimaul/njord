package io.madrona.njord.layers

import io.madrona.njord.model.*

class Background : Layerable {

    override val key = "BACKGROUND"

    override fun layers(options: LayerableOptions) = sequenceOf(
            Layer(
                    id = "background",
                    paint = Paint(
                            backgroundColor = colorFrom("CHWHT"),
                            backgroundOpacity = 1
                    ),
                    type = LayerType.BACKGROUND,
                    source = null,
            )
    )
}