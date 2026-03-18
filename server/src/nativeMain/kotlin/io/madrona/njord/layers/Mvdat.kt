package io.madrona.njord.layers

import io.madrona.njord.model.Layer

/**
 * Geometry Primitives: Area
 *
 * Object: Vertical datum of data
 *
 * Acronym: M_VDAT
 *
 * Code: 311
 */
class Mvdat : Layerable("M_VDAT") {

    override fun layers(options: LayerableOptions): Sequence<Layer> = emptySequence()
}
