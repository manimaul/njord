package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.model.*
import io.madrona.njord.util.logger
import kotlinx.serialization.json.JsonElement

abstract class Layerable(
    customKey: String? = null
) {
    val log = logger()
    abstract fun layers(options: LayerableOptions): Sequence<Layer>
    val key: String = customKey ?: this::class.simpleName?.uppercase() ?: ""

    open val sourceLayer: String = key

    open suspend fun preTileEncode(feature: ChartFeature) {}

    private var pointLayerFromSymbolId = 0

    /**
     *
     */
    fun pointLayerFromSymbol(
        symbol: Symbol = Symbol.Property(),
        anchor: Anchor = Anchor.BOTTOM,
        iconOffset: Offset? = null,
        iconAllowOverlap: Boolean = true,
        iconRotate: IconRot? = null,
        iconRotationAlignment: IconRotationAlignment? = null,
    ): Layer {
        return Layer(
            id = "${key}_point_${++pointLayerFromSymbolId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = listOf(Filters.any, Filters.eqTypePoint).json,
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = symbol.property,
                iconAnchor = anchor,
                iconOffset = iconOffset?.property,
                iconAllowOverlap = iconAllowOverlap,
                iconRotate = iconRotate?.property,
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
                textField = label.property,
                textSize = 14f,
                textOffset = textOffset?.property,
                symbolPlacement = Placement.POINT,
            ),
            paint = Paint(
                textColor = colorFrom(labelColor, theme).json,
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
        filter: JsonElement? = null,
    ): Layer {
        return Layer(
            id = "${key}_line_${++lineLayerWithColorId}",
            type = LayerType.LINE,
            sourceLayer = sourceLayer,
            filter = filter ?: Filters.eqTypeLineStringOrPolygon,
            paint = Paint(
                lineColor = colorFrom(color, theme).json,
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
        filter: List<JsonElement>? = null,
    ): Layer {
        return Layer(
            id = "${key}_line_${++lineLayerWithColorId}",
            type = LayerType.LINE,
            sourceLayer = sourceLayer,
            filter = filter?.json ?: Filters.eqTypeLineStringOrPolygon,
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
                textField = label.property,
                textSize = 14f,
                symbolPlacement = Placement.LINE,
            ),
            paint = Paint(
                textColor = colorFrom(labelColor, theme).json,
                textHaloColor = colorFrom(highlightColor, theme),
                textHaloWidth = 2.5f
            )
        )
    }

    private var lineLayerWithPatternId = 0
    fun lineLayerWithPattern(
        symbol: Sprite? = null,
        includePolygonLines: Boolean = true,
        symbolPlacement: Placement = Placement.LINE,
        iconRotationAlignment: IconRotationAlignment? = null, //defaults to auto
        iconAllowOverlap: Boolean? = null,
    ): Layer {
        return Layer(
            id = "${key}_line_symbol_${++lineLayerWithPatternId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = if (includePolygonLines) Filters.eqTypeLineStringOrPolygon else Filters.eqTypeLineString,
            layout = Layout(
                symbolPlacement = symbolPlacement,
                iconImage = symbol?.name?.json ?: listOf("get", "LP").json,
                iconAnchor = Anchor.CENTER,
                iconRotationAlignment = iconRotationAlignment,
                iconKeepUpright = false,
                iconAllowOverlap = iconAllowOverlap,
            )
        )
    }

    private var lineLayerWithPattern2Id = 0
    fun lineLayerWithPattern(
        symbol: Sprite? = null,
        iconRotate: Float? = null,
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
                iconImage = symbol?.name?.json ?: listOf("get", "LP").json,
                iconRotate = iconRotate?.json,
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
                textField = listOf("get".json, textKey.json).json,
                textSize = 14f,
                symbolPlacement = Placement.LINE,
            ),
            paint = Paint(
                textColor = colorFrom(Color.CHBLK, theme).json,
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
                fillColor = colorFrom(color, theme).json
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
                fillPattern = symbol?.name?.json ?: listOf("get", "AP").json
            )
        )
    }

    private var areaLayerWithSingleSymbolId = 0
    fun areaLayerWithSingleSymbol(
        symbol: Sprite? = null,
        iconOffset: List<Float>? = null,
        anchor: Anchor = Anchor.CENTER,
        iconRotationAlignment: IconRotationAlignment = IconRotationAlignment.MAP,
    ): Layer {
        return Layer(
            id = "${key}_area_symbol${++areaLayerWithSingleSymbolId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypePolyGon,
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = symbol?.json ?: listOf("get", "SY").json,
                iconOffset = iconOffset?.map { it.json }?.json,
                iconAnchor = anchor,
                iconRotationAlignment = iconRotationAlignment,
            )
        )
    }


    private var areaLayerWithPointSymbolId = 0
    fun areaLayerWithPointSymbol(
        symbol: Sprite? = null,
        anchor: Anchor = Anchor.CENTER,
        iconRotate: IconRot? = null,
        iconRotationAlignment: IconRotationAlignment? = null,
        iconAllowOverlap: Boolean = true,
        iconOffset: List<Float>? = null,
    ): Layer {
        return Layer(
            id = "${key}_area_point${++areaLayerWithPointSymbolId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = listOf(Filters.all, Filters.eqTypePolyGon, listOf("!=", "EA", true)).json,
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = symbol?.json ?: listOf("get", "SY").json,
                iconAnchor = anchor,
                iconRotate = iconRotate?.property,
                iconRotationAlignment = iconRotationAlignment,
                iconAllowOverlap = iconAllowOverlap,
                iconKeepUpright = false,
                iconOffset = iconOffset?.map { it.json }?.json,
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
        textOffset: Offset? = null,
        textOptional: Boolean? = null,
    ): Layer {
        return Layer(
            id = "${key}_area_label${++areaLayerWithTextId}",
            type = LayerType.SYMBOL,
            sourceLayer = sourceLayer,
            filter = Filters.eqTypePointOrPolygon,
            layout = Layout(
                textFont = listOf(Font.ROBOTO_BOLD),
                textJustify = textJustify,
                textField = label.property,
                textSize = 14f,
                textOffset = textOffset?.property,
                textOptional = textOptional,
            ),
            paint = Paint(
                textColor = colorFrom(textColor, theme).json,
                textHaloColor = colorFrom(haloColor, theme),
                textHaloWidth = 2.5f
            )
        )
    }
}


data class LayerableOptions(
    val depth: Depth,
    val theme: Theme
)

fun ChartFeature.excludeAreaPointSymbol() {
    props["EA"] = true.json
}

fun ChartFeature.pointSymbol(symbol: Sprite) {
    props["SY"] = symbol.name.json
}

fun ChartFeature.pointSymbol(symbol: Sprite, num: Int) {
    props["SY$num"] = symbol.name.json
}

fun ChartFeature.areaPattern(pattern: Sprite) {
    props["AP"] = pattern.name.json
}

fun ChartFeature.areaColor(color: Color) {
    props["AC"] = color.name.json
}

fun ChartFeature.linePattern(pattern: Sprite) {
    props["LP"] = pattern.name.json
}

fun ChartFeature.lineColor(color: Color) {
    props["LC"] = color.name.json
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
