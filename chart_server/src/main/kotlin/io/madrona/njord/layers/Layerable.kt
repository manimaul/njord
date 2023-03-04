package io.madrona.njord.layers

import io.madrona.njord.model.*
import io.madrona.njord.util.logger

abstract class Layerable {
    val log = logger()
    open val key = javaClass.simpleName.uppercase()
    abstract fun layers(options: LayerableOptions): Sequence<Layer>

    open fun preTileEncode(feature: ChartFeature) {
        log.warn("layer $key preTileEncode not handled")
    }
}

data class LayerableOptions(
    val depth: Depth
)

abstract class LayerableTodo : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        log.warn("layer $key preTileEncode not handled")
        feature.props["SY"] = "QUESMRK1"
        feature.props["AP"] = "QUESMRK1"
        feature.props["LP"] = "QUESMRK1"
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_fill",
            type = LayerType.FILL,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                fillColor = colorFrom("RADLO")
            ),
        ),
        Layer(
            id = "${key}_fill_pattern",
            type = LayerType.FILL,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                fillPattern = listOf("get", "AP")
            )
        ),
        Layer(
            id = "${key}_line",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypeLineStringOrPolygon,
            paint = Paint(
                lineColor = colorFrom("CHBLK"),
                lineWidth = 2f,
            ),
        ),
        Layer(
            id = "${key}_line_dash",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypeLineStringOrPolygon,
            paint = Paint(
                lineColor = colorFrom("LITRD"),
                lineWidth = 2f,
                lineDashArray = listOf(1f, 2f),
            ),
        ),
        Layer(
            id = "${key}_line_symbol",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = Filters.eqTypeLineStringOrPolygon,
            layout = Layout(
                symbolPlacement = Placement.LINE,
                iconImage = listOf("get", "LP"),
                iconAnchor = Anchor.CENTER,
                iconKeepUpright = false,
            )
        ),
        Layer(
            id = "${key}_point",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = listOf(Filters.any, Filters.eqTypePoint),
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = listOf("get", "SY"),
                iconAnchor = Anchor.BOTTOM,
                iconAllowOverlap = true,
                iconKeepUpright = false,
            )
        ),
    ).also {
        log.warn("layer $key layers not handled")
    }
}
