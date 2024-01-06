package io.madrona.njord.layers

import com.fasterxml.jackson.annotation.JsonProperty
import io.madrona.njord.model.*
import io.madrona.njord.util.logger
import org.w3c.dom.Text

abstract class Layerable(
    customKey: String? = null
) {
    val log = logger()
    abstract fun layers(options: LayerableOptions): Sequence<Layer>
    val key: String = customKey ?: javaClass.simpleName.uppercase()

    open val sourceLayer: String = key

    open suspend fun preTileEncode(feature: ChartFeature) {
        log.warn("layer $key preTileEncode not handled")
    }

    private var pointLayerFromSymbolId = 0

    /**
     *
     */
    fun pointLayerFromSymbol(
        symbol: Sprite? = null,
        anchor: Anchor = Anchor.BOTTOM,
        iconOffset: Offset? = null,
        iconAllowOverlap: Boolean = true,
        iconKeepUpright: Boolean = false,
        iconRotate: Any? = null,
        iconRotationAlignment: IconRotationAlignment? = null,
    ): Layer {
        return Layer(
            id = "${key}_point_${++pointLayerFromSymbolId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = listOf(Filters.any, Filters.eqTypePoint),
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = symbol ?: listOf("get", "SY"),
                iconAnchor = anchor,
                iconOffset = iconOffset?.property,
                iconAllowOverlap = iconAllowOverlap,
                iconKeepUpright = iconKeepUpright,
                iconRotate = iconRotate,
                iconRotationAlignment = iconRotationAlignment,
            )
        )
    }

    private var pointLayerWithLabelId = 0
    fun pointLayerWithLabel(
        label: Label,
        theme: Theme,
        labelColor: Color = Color.SNDG2,
        highlightColor: Color = Color.DEPDW,
        textAnchor: Anchor? = null,
        textJustify: TextJustify = TextJustify.CENTER,
        textOffset: Offset? = null
    ): Layer {
        return Layer(
            id = "${key}_label_${++lineLayerWithLabelId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypePoint,
            layout = Layout(
                textFont = listOf(Font.ROBOTO_BOLD),
                textAnchor = textAnchor,
                textJustify = textJustify,
                textField = label.label,
                textSize = 14f,
                textOffset = textOffset?.property,
                symbolPlacement = Placement.POINT,
            ),
            paint = Paint(
                textColor = colorFrom(labelColor, theme),
                textHaloColor = colorFrom(highlightColor, theme),
                textHaloWidth = 2.5f
            )
        )
    }

    private var lineLayerWithColorId = 0
    fun lineLayerWithColor(
        theme: Theme,
        color: Color,
        style: LineStyle = LineStyle.Solid,
        width: Float = 2f,
        filter: List<Any>? = null,
    ): Layer {
        return Layer(
            id = "${key}_line_${++lineLayerWithColorId}",
            type = LayerType.LINE,
            sourceLayer = sourceLayer,
            filter = filter ?: Filters.eqTypeLineStringOrPolygon,
            paint = Paint(
                lineColor = colorFrom(color, theme),
                lineWidth = width,
                lineDashArray = style.lineDashArray
            )
        )
    }

    fun lineLayerWithColor(
        options: Set<Color>,
        theme: Theme,
        style: LineStyle = LineStyle.Solid,
        width: Float = 2f,
        filter: List<Any>? = null,
    ): Layer {
        return Layer(
            id = "${key}_line_${++lineLayerWithColorId}",
            type = LayerType.LINE,
            sourceLayer = sourceLayer,
            filter = filter ?: Filters.eqTypeLineStringOrPolygon,
            paint = Paint(
                lineColor = Filters.lineColor(options = options, theme = theme),
                lineWidth = width,
                lineDashArray = style.lineDashArray
            )
        )
    }

    private var lineLayerWithLabelId = 0
    fun lineLayerWithLabel(
        label: Label,
        theme: Theme,
        labelColor: Color = Color.SNDG2,
        highlightColor: Color = Color.DEPDW,
    ): Layer {
        return Layer(
            id = "${key}_label_${++lineLayerWithLabelId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypeLineStringOrPolygon,
            layout = Layout(
                textFont = listOf(Font.ROBOTO_BOLD),
                textJustify = TextJustify.CENTER,
                textField = label.label,
                textSize = 14f,
                symbolPlacement = Placement.LINE,
            ),
            paint = Paint(
                textColor = colorFrom(labelColor, theme),
                textHaloColor = colorFrom(highlightColor, theme),
                textHaloWidth = 2.5f
            )
        )
    }

    private var lineLayerWithPatternId = 0
    fun lineLayerWithPattern(symbol: Sprite? = null): Layer {
        return Layer(
            id = "${key}_line_symbol_${++lineLayerWithPatternId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypeLineStringOrPolygon,
            layout = Layout(
                symbolPlacement = Placement.LINE,
                iconImage = symbol?.name ?: listOf("get", "LP"),
                iconAnchor = Anchor.CENTER,
                iconKeepUpright = false,
            )
        )
    }

    private var lineLayerWithPattern2Id = 0
    fun lineLayerWithPattern(
        symbol: Sprite? = null,
        iconRotate: Any? = null,
        iconSize: Float? = null,
        spacing: Float? = null,
        allowOverlap: Boolean,
    ): Layer {
        return Layer(
            id = "${key}_line_symbol_${++lineLayerWithPattern2Id}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypeLineStringOrPolygon,
            layout = Layout(
                symbolPlacement = Placement.LINE,
                iconImage = symbol?.name ?: listOf("get", "LP"),
                iconRotate = iconRotate,
                iconRotationAlignment = IconRotationAlignment.MAP,
                iconAllowOverlap = allowOverlap,
                iconSize = iconSize,
                symbolSpacing = spacing,
            )
        )
    }

    private var lineLayerWithTextId = 0
    fun lineLayerWithText(textKey: String, theme: Theme): Layer {
        return Layer(
            id = "${key}_line_label${++lineLayerWithTextId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypeLineString,
            layout = Layout(
                textFont = listOf(Font.ROBOTO_BOLD),
                textJustify = TextJustify.CENTER,
                textField = listOf("get", textKey),
                textSize = 14f,
                symbolPlacement = Placement.LINE,
            ),
            paint = Paint(
                textColor = colorFrom(Color.CHBLK, theme),
                textHaloColor = colorFrom(Color.CHWHT, theme),
                textHaloWidth = 2.5f
            )
        )
    }

    private var areaLayerWithFillColorId = 0
    fun areaLayerWithFillColor(
        options: Set<Color>,
        theme: Theme
    ): Layer {
        return Layer(
            id = "${key}_fill_${++areaLayerWithFillColorId}",
            type = LayerType.FILL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                fillColor = Filters.areaFillColor(options = options, theme = theme)
            ),
        )
    }

    fun areaLayerWithFillColor(
        color: Color,
        theme: Theme
    ): Layer {
        return Layer(
            id = "${key}_fill_${++areaLayerWithFillColorId}",
            type = LayerType.FILL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                fillColor = colorFrom(color, theme)
            ),
        )
    }

    private var areaLayerWithFillPatternId = 0
    fun areaLayerWithFillPattern(symbol: Sprite? = null): Layer {
        return Layer(
            id = "${key}_fill_pattern_${++areaLayerWithFillColorId}",
            type = LayerType.FILL,
            sourceLayer = sourceLayer,
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
    ): Layer {
        return Layer(
            id = "${key}_area_symbol${++areaLayerWithSingleSymbolId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
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


    private var areaLayerWithPointSymbolId = 0
    fun areaLayerWithPointSymbol(
        symbol: Sprite? = null,
        anchor: Anchor = Anchor.CENTER,
        iconRotate: Any? = null,
        iconRotationAlignment: IconRotationAlignment? = null,
        iconAllowOverlap: Boolean = true,
        iconOffset: List<Float>? = null,
    ): Layer {
        return Layer(
            id = "${key}_area_point${++areaLayerWithPointSymbolId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = listOf(Filters.all, Filters.eqTypePolyGon, listOf("!=", "EA", true)),
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = symbol ?: listOf("get", "SY"),
                iconAnchor = anchor,
                iconRotate = iconRotate,
                iconRotationAlignment = iconRotationAlignment,
                iconAllowOverlap = iconAllowOverlap,
                iconKeepUpright = false,
                iconOffset = iconOffset,
            )
        )
    }

    private var areaLayerWithTextId = 0
    fun areaLayerWithText(
       label: Label,
       theme: Theme,
       textColor: Color = Color.CHBLK,
       haloColor: Color = Color.CHWHT,
       textJustify: TextJustify = TextJustify.CENTER,
    ): Layer {
        return Layer(
            id = "${key}_area_label${++areaLayerWithTextId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypePointOrPolygon,
            layout = Layout(
                textFont = listOf(Font.ROBOTO_BOLD),
                textJustify = textJustify,
                textField = label.label,
                textSize = 14f,
//                symbolPlacement = Placement.POINT,
            ),
            paint = Paint(
                textColor = colorFrom(textColor, theme),
                textHaloColor = colorFrom(haloColor, theme),
                textHaloWidth = 2.5f
            )
        )
    }
}

enum class ThemeMode: Theme {
    @JsonProperty("DAY") Day,
    @JsonProperty("DUSK") Dusk,
    @JsonProperty("NIGHT") Night;
}

data class CustomTheme(
    val mode: ThemeMode,
    val name: String
) : Theme

sealed interface Theme

data class LayerableOptions(
    val depth: Depth,
    val theme: Theme
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
    override suspend fun preTileEncode(feature: ChartFeature) {
        log.warn("layer $key preTileEncode not handled")
        feature.pointSymbol(Sprite.QUESMRK1)
        feature.areaPattern(Sprite.QUESMRK1)
        feature.linePattern(Sprite.QUESMRK1)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(color = Color.RADLO, theme = options.theme),
        areaLayerWithFillPattern(),
        lineLayerWithColor(options.theme, color = Color.CHBLK),
        lineLayerWithColor(options.theme, color = Color.LITRD, LineStyle.DashLine),
        lineLayerWithPattern(),
        pointLayerFromSymbol(),
    ).also {
        log.warn("layer $key layers not handled")
    }
}
