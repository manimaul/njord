package io.madrona.njord.layers

/**
 * Geometry Primitives: Line
 *
 * Object: Cable, overhead
 *
 * Acronym: CBLOHD
 */
class Cblohd : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            color = Color.CHBLK,
            width = 0.5f,
            style = LineStyle.CustomDash(10f, 5f)
        )
    )
}
