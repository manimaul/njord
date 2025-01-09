package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Land area
 *
 * Acronym: LNDARE
 *
 * Code: 71
 */
open class LndareLabel : Layerable() {

    override val sourceLayer: String = "LNDARE"
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            areaLayerWithText(
                label = Label.Property("OBJNAM"),
                theme = options.theme,
                haloColor = Color.LANDA,
                textOptional = true,
            ),
        )
    }
}
