package io.madrona.njord.layers.set

import io.madrona.njord.layers.Ply
import io.madrona.njord.layers.Soundg

/**
 */
class ExtraLayers {
    val layers = sequenceOf(
        Soundg(),
        Ply(),
    )
}
