package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Sea area / named water area
 *
 * Acronym: SEAARE
 *
 * Code: 119
 */
class Seaare : Layerable() {

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            areaLayerWithText(
                label = Label.Property("OBJNAM"),
                theme = options.theme,
                textColor = Color.SNDG2,
                haloColor = Color.DEPDW,
                textJustify = TextJustify.LEFT,
            ),
        )
    }
}