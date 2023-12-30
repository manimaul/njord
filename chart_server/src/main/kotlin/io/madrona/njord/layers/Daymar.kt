package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Topshp
import io.madrona.njord.layers.attributehelpers.Topshp.Companion.topshp
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Daymark
 *
 * Acronym: DAYMAR
 *
 * Code: 39
 */
class Daymar : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        when (feature.topshp()) {
            Topshp.SQUARE,
            Topshp.RECTANGLE_HORIZONTAL,
            Topshp.RECTANGLE_VERTICAL -> feature.pointSymbol(Sprite.DAYSQR01)

            Topshp.TRIANGLE_POINT_UP -> feature.pointSymbol(Sprite.DAYTRI01)
            Topshp.TRIANGLE_POINT_DOWN -> feature.pointSymbol(Sprite.DAYTRI05)
            else -> feature.pointSymbol(Sprite.DAYSQR01)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            iconOffset = listOf(0f, -18f)
        )
    )
}
