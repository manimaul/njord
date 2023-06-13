package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Watlev
import io.madrona.njord.layers.attributehelpers.Watlev.Companion.watlev
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Causeway
 *
 * Acronym: CAUSWY
 *
 * Code: 26
 */
class Causwy : Layerable() {
    private val lineColor = Color.CSTLN

    override fun preTileEncode(feature: ChartFeature) {
        when (feature.watlev()) {
            Watlev.PARTLY_SUBMERGED_AT_HIGH_WATER,
            Watlev.ALWAYS_UNDER_WATER_SUBMERGED,
            Watlev.AWASH,
            Watlev.SUBJECT_TO_INUNDATION_OR_FLOODING,
            Watlev.COVERS_AND_UNCOVERS -> feature.areaColor(Color.DEPIT)
            Watlev.ALWAYS_DRY,
            Watlev.FLOATING,
            null -> feature.areaColor(Color.CHBRN)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(),
        lineLayerWithColor(lineColor, style = LineStyle.DashLine)
    )
}
