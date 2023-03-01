package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.layers.attributehelpers.Catobs
import io.madrona.njord.layers.attributehelpers.Conrad
import io.madrona.njord.model.*

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
        Layer(
            id = "${key}_line",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypeLineStringOrPolygon,
            paint = Paint(
                lineColor = colorFrom("CHGRD"),
                lineWidth = 0.5f,
            ),
        ),
        Layer(
            id = "${key}_line_symbol",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = Filters.eqTypeLineStringOrPolygon,
            layout = Layout(
                symbolPlacement = Placement.LINE,
                iconImage = listOf("get", "SY"),
                iconAnchor = Anchor.CENTER,
                iconKeepUpright = false,
            )
        ),
    )

    override fun preTileEncode(feature: ChartFeature) {
        when (feature.props.intValue("CONRAD")?.let { Conrad.fromId(it) }) {
            Conrad.RADAR_CONSPICUOUS,
            Conrad.RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR -> {
                feature.props["SY"] = "RACNSP01"
            }
            Conrad.NOT_RADAR_CONSPICUOUS,
            null -> Unit
        }

    }
}
