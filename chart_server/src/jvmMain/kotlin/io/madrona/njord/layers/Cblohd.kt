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
            theme = options.theme,
            color = Color.CHBLK,
            width = 0.5f,
            style = LineStyle.CustomDash(10f, 5f)
        )
    )
}
