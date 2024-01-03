package io.madrona.njord.layers

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Fishing facility
 *
 * Acronym: FSHFAC
 *
 * Code: 55
 */
class Fshfac : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
            pointLayerFromSymbol(),
    )
}
