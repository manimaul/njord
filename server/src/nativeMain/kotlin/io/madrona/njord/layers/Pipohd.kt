package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Conrad
import io.madrona.njord.layers.attributehelpers.Conrad.Companion.conrad
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line
 *
 * Object: Pipeline overhead
 *
 * Acronym: PIPOHD
 *
 * Code: 85
 *
 * S-52 lookup: `LS(SOLD,3,CHGRD)`, plus `SY(RACNSP01)` when radar conspicuous (CONRAD 1 or 3).
 * The `clr` text is drawn by [ClearanceLabel].
 */
class Pipohd : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.conrad()) {
            Conrad.RADAR_CONSPICUOUS,
            Conrad.RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR -> feature.linePattern(Sprite.RACNSP01)

            Conrad.NOT_RADAR_CONSPICUOUS,
            null -> Unit
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.CHGRD, width = 2f),
        lineLayerWithPattern(
            includePolygonLines = false,
            symbolPlacement = Placement.LINE_CENTER,
            iconRotationAlignment = IconRotationAlignment.VIEWPORT,
            iconAllowOverlap = true,
        ),
    )
}
