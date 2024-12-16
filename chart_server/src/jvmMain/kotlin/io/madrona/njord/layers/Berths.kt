package io.madrona.njord.layers

import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Berth
 *
 * Acronym: BERTHS
 *
 * Code: 10
 */
class Berths : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.BRTHNO01)
        feature.lineColor(Color.CHMGD)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithPointSymbol(),
        pointLayerFromSymbol(),
        lineLayerWithColor(
            color = Color.CHMGD,
            theme = options.theme,
            style = LineStyle.DashLine
        ),
        areaLayerWithText(
            label = Label.Property("OBJNAM"),
            theme = options.theme,
            textColor = Color.CHBLK,
            haloColor = Color.CHWHT,
        ),
        pointLayerWithLabel(
            label = Label.Property("OBJNAM"),
            theme = options.theme,
            labelColor = Color.CHBLK,
            highlightColor = Color.CHWHT,
        )
    )
}
