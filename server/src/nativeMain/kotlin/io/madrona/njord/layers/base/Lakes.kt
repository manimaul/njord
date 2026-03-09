package io.madrona.njord.layers.base

import io.madrona.njord.layers.Lakare
import io.madrona.njord.layers.Layerable
import io.madrona.njord.layers.LayerableOptions
import io.madrona.njord.model.Color

/**
 * Natural Earth base layer: lakes
 */
class Lakes : Lakare() {
    override val sourceLayer: String = "lakes"
}