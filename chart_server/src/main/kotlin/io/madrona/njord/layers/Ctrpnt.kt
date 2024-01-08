package io.madrona.njord.layers

import io.madrona.njord.model.Anchor
import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point
 *
 * Object: Control point
 *
 * Acronym: CTRPNT
 *
 * Code: 33
 */
class Ctrpnt : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.POSGEN04)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        pointLayerWithLabel(
            label = Label.Property("OBJNAM"),
            textAnchor = Anchor.BOTTOM_LEFT,
            textOffset = Offset.Coord(x = 1f, y = 0f),
            highlightColor = Color.LANDA,
            theme = options.theme
        )
    )
}
