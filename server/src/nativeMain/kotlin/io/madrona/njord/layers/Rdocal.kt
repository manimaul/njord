package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.stringValue
import io.madrona.njord.layers.attributehelpers.Trafic.Companion.trafic
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line
 *
 * Object: Radio calling-in point
 *
 * Acronym: RDOCAL
 *
 * Code: 104
 */
class Rdocal : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        if (feature.props.containsKey("ORIENT") && feature.trafic() != null) {
            feature.pointSymbol(Sprite.RDOCAL02)
        } else {
            feature.pointSymbol(Sprite.RCLDEF01)
        }
        // S-52: TE('Nr %s','OBJNAM',...,1,-1,...) above TE('ch %s','COMCHA',...,1,1,...), both
        // left justified one text body right of the symbol.
        listOfNotNull(
            feature.props.stringValue("OBJNAM")?.let { "Nr $it" },
            feature.props.stringValue("COMCHA")?.let { "ch $it" },
        ).takeIf { it.isNotEmpty() }?.let {
            feature.props["_L"] = it.joinToString("\n").json
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            iconRotate = IconRot.Property("ORIENT"),
        ), areaLayerWithPointSymbol(
            iconRotate = IconRot.Property("ORIENT"),
        ), lineLayerWithLabel(
            label = Label.Property("_L"),
            theme = options.theme,
        ), pointLayerWithLabel(
            label = Label.Property("_L"),
            theme = options.theme,
            labelColor = Color.CHBLK,
            highlightColor = Color.CHWHT,
            textAnchor = Anchor.LEFT,
            textJustify = TextJustify.LEFT,
            textOffset = Offset.Coord(x = 1.2f, y = 0f),
        )
    )
}
