package io.madrona.njord.layers

import io.madrona.njord.model.Layer
import io.madrona.njord.model.LayerType
import io.madrona.njord.model.Paint
import io.madrona.njord.model.colorFrom

class Background : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
            Layer(
                    id = "background",
                    paint = Paint(
                            backgroundColor = colorFrom("NODTA"),
                            backgroundOpacity = 1
                    ),
                    type = LayerType.BACKGROUND,
                    source = null,
            )
    )
}