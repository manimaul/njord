package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Traffic Separation Scheme Boundary
 *
 * Acronym: TSSBND
 *
 * Code: 146
 */
class Tssbnd : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_line_dash",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypeLineString,
            paint = Paint(
                lineColor = colorFrom("TRFCF"),
                lineWidth = 2f,
                lineDashArray = listOf(3f, 2f),
            ),
        ),
    )
}