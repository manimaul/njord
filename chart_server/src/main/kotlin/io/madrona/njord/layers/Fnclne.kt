package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Convis
import io.madrona.njord.layers.attributehelpers.Convis.Companion.convis
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Fence/wall
 *
 * Acronym: FNCLNE
 *
 * Code: 52
 */
class Fnclne : Layerable() {

    private val lineColors = setOf(Color.CHBLK, Color.LANDF)
    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.convis()) {
            Convis.VISUAL_CONSPICUOUS -> feature.lineColor(Color.CHBLK)
            Convis.NOT_VISUAL_CONSPICUOUS,
            null -> feature.lineColor(Color.LANDF)
        }
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            lineLayerWithColor(
                options = lineColors,
                theme = options.theme,
                width = 1f,
            ),
        )
    }
}
