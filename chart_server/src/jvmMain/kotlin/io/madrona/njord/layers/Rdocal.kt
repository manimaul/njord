package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.stringValue
import io.madrona.njord.layers.attributehelpers.Trafic.Companion.trafic
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Sprite

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
        feature.props.stringValue("COMCHA")?.let {
            feature.props["_L"] = "ch $it".json
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
        )
    )
}
