package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Anchorage area
 *
 * Acronym: ACHARE
 *
 * Code: 4
 */
class Achare(
    customKey: String? = null
) : Layerable(customKey) {
    private val lineColor = Color.CHMGF
    override fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.ACHARE02)
        feature.linePattern(Sprite.ACHARE02)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(
            theme = options.theme,
            color = lineColor,
            width = 2f,
            style = LineStyle.CustomDash(3f, 4f)
        ),
        lineLayerWithPattern(),
        lineLayerWithLabel(
            theme = options.theme,
            label = Label.Property("OBJNAM")
        ),
    )

//    private val color = Color.CHMGF
//    private val pattern = Sprite.ACHARE51
//    private val symbol = Sprite.ACHARE02
//    override fun preTileEncode(feature: ChartFeature) {
//        feature.pointSymbol(symbol)
//        feature.linePattern(pattern)
//    }
//    override fun layers(options: LayerableOptions) = sequenceOf(
//        pointLayerFromSymbol(symbol),
//        lineLayerWithPattern(
//            pattern,
//            // https://maplibre.org/maplibre-style-spec/layers/#layout-symbol-icon-rotation-alignment
//            // map orientation aligns x-axis with line
//            spacing = 1f,
//            allowOverlap = false
//        ),
//    )
}
