package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Conrad
import io.madrona.njord.layers.attributehelpers.Conrad.Companion.conrad
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Color
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Conveyor
 *
 * Acronym: CONVYR
 *
 * Code: 34
 */
class Convyr : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.CHGRD, width = 0.5f),
        lineLayerWithPattern(),
    )

    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.conrad()) {
            Conrad.RADAR_CONSPICUOUS,
            Conrad.RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR -> {
                feature.linePattern(Sprite.RACNSP01)
            }
            Conrad.NOT_RADAR_CONSPICUOUS,
            null -> Unit
        }
    }
}
