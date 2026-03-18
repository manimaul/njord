package io.madrona.njord.layers

import io.madrona.njord.model.Layer

/**
 * Geometry Primitives: Area
 *
 * Object: Sounding datum
 *
 * Acronym: M_SDAT
 *
 * Code: 309
 */
class Msdat : Layerable("M_SDAT") {

    override fun layers(options: LayerableOptions): Sequence<Layer> = emptySequence()
}
