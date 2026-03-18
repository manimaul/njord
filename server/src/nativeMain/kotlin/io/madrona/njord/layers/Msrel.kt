package io.madrona.njord.layers

import io.madrona.njord.model.Layer

/**
 * Geometry Primitives: Area
 *
 * Object: Survey reliability
 *
 * Acronym: M_SREL
 *
 * Code: 310
 */
class Msrel : Layerable("M_SREL") {

    override fun layers(options: LayerableOptions): Sequence<Layer> = emptySequence()
}
