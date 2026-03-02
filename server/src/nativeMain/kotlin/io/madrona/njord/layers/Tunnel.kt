package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.geo.symbols.stringValue
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Tunnel
 *
 * Acronym: TUNNEL
 *
 * Code: 151
 *
 * Attributes: BURDEP, CONDTN, HORACC, HORCLR, NOBJNM, OBJNAM, STATUS, VERACC, VERCLR
 */
class Tunnel : Layerable() {
    private val areaFillColors = setOf(Color.DEPVS)
    private val lineColors = setOf(Color.CHBLK, Color.CHGRD)

    override suspend fun preTileEncode(feature: ChartFeature) {
        if (feature.props.floatValue("BURDEP") != null) {
            feature.areaColor(Color.DEPVS)
            feature.lineColor(Color.CHBLK)
        } else {
            feature.lineColor(Color.CHGRD)
        }
        val name = feature.props.stringValue("NOBJNM") ?: feature.props.stringValue("OBJNAM")
        name?.let { feature.props["_name"] = it.json }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, options = areaFillColors),
        lineLayerWithColor(options = lineColors, theme = options.theme, style = LineStyle.DashLine, width = 1f),
        areaLayerWithText(label = Label.Property("_name"), theme = options.theme, textColor = Color.CHBLK, haloColor = Color.CHWHT),
        lineLayerWithLabel(label = Label.Property("_name"), theme = options.theme, labelColor = Color.CHBLK, highlightColor = Color.CHWHT),
        pointLayerWithLabel(label = Label.Property("_name"), theme = options.theme, labelColor = Color.CHBLK, highlightColor = Color.CHWHT),
    )
}
