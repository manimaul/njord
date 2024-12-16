package io.madrona.njord.layers

import io.madrona.njord.model.Anchor
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.IconRotationAlignment

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Dumping ground
 *
 * Acronym: DMPGRD
 *
 * Code: 48
 */
class Dmpgrd : Layerable() {
    private val lineColor = Color.CHMGD

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CHINFO07)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP,
        ),
        lineLayerWithColor(theme = options.theme, color = lineColor, width = 1f, style = LineStyle.DashLine)
    )
}
