package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Canal
 *
 * Acronym: CANALS
 *
 * Code: 23
 */
class Canals : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_line",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypeLineStringOrPolygon,
            paint = Paint(
                lineColor = colorFrom("CHBLK"),
                lineWidth = 0.5f,
            ),
        ),
        Layer(
            id = "${key}_fill",
            type = LayerType.FILL,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                fillColor = colorFrom("DEPVS")
            ),
        )
    )
}
