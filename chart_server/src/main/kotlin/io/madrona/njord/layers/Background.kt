package io.madrona.njord.layers

import io.madrona.njord.model.Layer
import io.madrona.njord.model.LayerType
import io.madrona.njord.model.Paint

object Background {

    fun layers() = sequenceOf(
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