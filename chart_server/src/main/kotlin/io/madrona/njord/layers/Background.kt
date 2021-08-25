package io.madrona.njord.layers

import io.madrona.njord.model.Layer
import io.madrona.njord.model.LayerType
import io.madrona.njord.model.Paint
import io.madrona.njord.model.StyleColor

class Background : Layerable {

    override val key = "BACKGROUND"

    override fun layers(color: StyleColor) = sequenceOf(
            Layer(
                    id = "background",
                    paint = Paint(
                            backgroundColor = "#000",
                            backgroundOpacity = 1
                    ),
                    type = LayerType.BACKGROUND
            )
    )
}