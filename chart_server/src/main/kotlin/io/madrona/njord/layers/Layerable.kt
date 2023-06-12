package io.madrona.njord.layers

import io.madrona.njord.model.*
import io.madrona.njord.util.logger
import java.awt.Point

abstract class Layerable(
    customKey: String? = null
) {
    val log = logger()
    abstract fun layers(options: LayerableOptions): Sequence<Layer>
    val key: String = customKey ?: javaClass.simpleName.uppercase()

    open fun preTileEncode(feature: ChartFeature) {
        log.warn("layer $key preTileEncode not handled")
    }

    private var pointLayerFromSymbolId = 0

    /**
     *
     */
    fun pointLayerFromSymbol(
        symbol: Sprite? = null,
        anchor: Anchor = Anchor.BOTTOM,
        iconOffset: List<Float>? = null,
        iconAllowOverlap: Boolean = true,
        iconKeepUpright: Boolean = false,
        iconRotate: Any? = null,
        iconRotationAlignment: IconRotationAlignment? = null,
    ): Layer {
        return Layer(
            id = "${key}_point_${++pointLayerFromSymbolId}",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = listOf(Filters.any, Filters.eqTypePoint),
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = symbol ?: listOf("get", "SY"),
                iconAnchor = anchor,
                iconOffset = iconOffset,
                iconAllowOverlap = iconAllowOverlap,
                iconKeepUpright = iconKeepUpright,
                iconRotate = iconRotate,
                iconRotationAlignment = iconRotationAlignment,
            )
        )
    }

    private var lineLayerWithColorId = 0
    fun lineLayerWithColor(
        color: Color? = null,
        style: LineStyle = LineStyle.Solid,
        width: Float = 2f,
        filter: List<Any>? = null,
    ): Layer {
        return Layer(
            id = "${key}_line_${++lineLayerWithColorId}",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = filter ?: Filters.eqTypeLineStringOrPolygon,
            paint = Paint(
                lineColor = color?.let { colorFrom(color.name) } ?: Filters.lineColor,
                lineWidth = width,
                lineDashArray = style.lineDashArray
            )
        )
    }

    private var lineLayerWithPatternId = 0
    fun lineLayerWithPattern(symbol: Sprite? = null): Layer {
        return Layer(
            id = "${key}_line_symbol_${++lineLayerWithPatternId}",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = Filters.eqTypeLineStringOrPolygon,
            layout = Layout(
                symbolPlacement = Placement.LINE,
                iconImage = symbol?.name ?: listOf("get", "LP"),
                iconAnchor = Anchor.CENTER,
                iconKeepUpright = false,
            )
        )
    }

    private var areaLayerWithFillColorId = 0
    fun areaLayerWithFillColor(color: Color? = null): Layer {
        return Layer(
            id = "${key}_fill_${++areaLayerWithFillColorId}",
            type = LayerType.FILL,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                fillColor = color?.let { colorFrom(it.name) } ?: Filters.areaFillColor
            ),
        )
    }

    private var areaLayerWithFillPatternId = 0
    fun areaLayerWithFillPattern(symbol: Sprite? = null): Layer {
        return Layer(
            id = "${key}_fill_pattern_${++areaLayerWithFillColorId}",
            type = LayerType.FILL,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                fillPattern = symbol?.name ?: listOf("get", "AP")
            )
        )
    }

    private var areaLayerWithSingleSymbolId = 0
    fun areaLayerWithSingleSymbol(
        symbol: Sprite? = null,
        iconOffset: List<Float>? = null
    ) : Layer {
        return Layer(
            id = "${key}_area_symbol${++areaLayerWithSingleSymbolId}",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = symbol ?: listOf("get", "SY"),
                iconOffset = iconOffset,
                iconAnchor = Anchor.CENTER,
                iconKeepUpright = false,
            )
        )
    }

    fun areaLayerWithPointSymbol(symbol: Sprite? = null, anchor: Anchor = Anchor.CENTER): Layer {
        return Layer(
            id = "${key}_area_point",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = listOf(Filters.all, Filters.eqTypePolyGon, listOf("!=", "EA", true)),
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = listOf("get", "SY"),
                iconAnchor = Anchor.CENTER,
                iconAllowOverlap = true,
                iconKeepUpright = false,
            )
        )
    }
}

data class LayerableOptions(
    val depth: Depth
)

fun ChartFeature.excludeAreaPointSymbol() {
    props["EA"] = true
}

fun ChartFeature.pointSymbol(symbol: Sprite) {
    props["SY"] = symbol.name
}

fun ChartFeature.areaPattern(pattern: Sprite) {
    props["AP"] = pattern.name
}

fun ChartFeature.areaColor(color: Color) {
    props["AC"] = color.name
}

fun ChartFeature.linePattern(pattern: Sprite) {
    props["LP"] = pattern.name
}

fun ChartFeature.lineColor(color: Color) {
    props["LC"] = color.name
}

abstract class LayerableTodo : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        log.warn("layer $key preTileEncode not handled")
        feature.pointSymbol(Sprite.QUESMRK1)
        feature.areaPattern(Sprite.QUESMRK1)
        feature.linePattern(Sprite.QUESMRK1)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(Color.RADLO),
        areaLayerWithFillPattern(),
        lineLayerWithColor(color = Color.CHBLK),
        lineLayerWithColor(color = Color.LITRD, LineStyle.DashLine),
        lineLayerWithPattern(),
        pointLayerFromSymbol(),
    ).also {
        log.warn("layer $key layers not handled")
    }
}
