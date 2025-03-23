package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.geo.symbols.stringValue
import io.madrona.njord.model.Anchor
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite
import io.madrona.njord.model.TextJustify

/**
 * Geometry Primitives: Point
 *
 * Object: Current - non - gravitational
 *
 * Acronym: CURENT
 *
 * Code: 36
 */
class Curent : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.props.floatValue("ORIENT")?.let {
            feature.pointSymbol(Sprite.CURENT01)
        } ?: feature.pointSymbol(Sprite.CURDEF01)

        StringBuilder(feature.props.stringValue("OBJNAM") ?: "").apply {
            feature.props.floatValue("CURVEL")?.takeIf { it > 0f }?.let { currentVelocity ->
                if (isNotEmpty()) {
                    append(' ')
                }
                append("$currentVelocity kts (max)")
            }
        }.takeIf { it.isNotBlank() }?.let { pointLabel ->
            feature.props["_PL"] = pointLabel.toString().json
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            iconRotate = IconRot.Property("ORIENT"),
        ),
        pointLayerWithLabel(
            label = Label.Property("INFORM"),
            theme = options.theme,
            textJustify = TextJustify.LEFT,
            textAnchor = Anchor.BOTTOM_LEFT,
            textOffset = Offset.Coord(x = 2f, y = 0f)
        ),
        pointLayerWithLabel(
            label = Label.Property("_PL"),
            theme = options.theme,
            textAnchor = Anchor.BOTTOM_RIGHT,
            textJustify = TextJustify.RIGHT,
            textOffset = Offset.Coord(x = -2f, y = 0f)
        )
    )
}
