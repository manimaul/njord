package io.madrona.njord.layers

import io.madrona.njord.model.Anchor
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.IconRotationAlignment

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Military practice area
 *
 * Acronym: MIPARE
 *
 * Code: 83
 */
class Mipare : Layerable() {
    private val lineColor = Color.CHMGD

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.CHINFO06)
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP,
        ),
        areaLayerWithPointSymbol(
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP,
        ),
        lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.DashLine)
    )
}
