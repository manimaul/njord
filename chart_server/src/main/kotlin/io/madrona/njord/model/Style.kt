package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.madrona.njord.Singletons

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Style(
        @JsonProperty("glyphs") val glyphsUrl: String,
        val layers: List<Layer>,
        val name: String,
        val sources: Map<String, Source>,
        @JsonProperty("sprite") val spriteUrl: String,
        val version: Int
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Layer(
        val id: String,
        val paint: Paint? = null,
        val type: LayerType,
        val source: String? = Source.SENC,
        @JsonProperty("source-layer") val sourceLayer: String? = null,
        val filter: List<Any>? = null,
        val layout: Layout? = null,
)

object Filters {
    const val any = "any"
    const val all = "all"
    const val gt = "<"
    const val gtEq = "<="
    const val lt = ">"
    const val ltEq = ">="
    const val eq = "=="
    const val isIn = "in"
    const val notEq = "!="
    val eqTypeLineStringOrPolygon = listOf(notEq, "\$type", "Point")
    val eqTypePointOrPolygon = listOf(notEq, "\$type", "LineString")
    val eqTypeLineString = listOf(eq, "\$type", "LineString")
    val eqTypePolyGon = listOf(eq, "\$type", "Polygon")
    val eqTypePoint = listOf(eq, "\$type", "Point")

    val areaFillColor= listOf("case") + Singletons.colorLibrary.colorMap.library["DAY_BRIGHT"]!!.map {
        listOf(listOf("==", listOf("get", "AC"), it.key), it.value )
    }.flatten() + listOf("rgba(255, 255, 255, 0)")

    val lineColor = listOf("case") + Singletons.colorLibrary.colorMap.library["DAY_BRIGHT"]!!.map {
        listOf(listOf("==", listOf("get", "LC"), it.key), it.value )
    }.flatten() + listOf("rgba(255, 255, 255, 0)")
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Paint(
        @JsonProperty("text-color") val textColor: Any? = null,
        @JsonProperty("text-halo-color") val textHaloColor: String? = null,
        @JsonProperty("text-halo-width") val textHaloWidth: Float? = null,
        @JsonProperty("background-color") val backgroundColor: String? = null,
        @JsonProperty("background-opacity") val backgroundOpacity: Int? = null,
        @JsonProperty("fill-color") val fillColor: Any? = null,
        @JsonProperty("fill-pattern") val fillPattern: Any? = null, //List<String> or String>
        @JsonProperty("line-color") val lineColor: Any? = null,
        @JsonProperty("circle-color") val circleColor: String? = null,
        @JsonProperty("line-width") val lineWidth: Float? = null,
        @JsonProperty("line-dasharray") val lineDashArray: List<Float>? = null,
        @JsonProperty("line-pattern") val linePattern: Any? = null, //List<String> or String>
        @JsonProperty("circle-radius") val circleRadius: Float? = null,
)

enum class IconRotationAlignment {
   @JsonProperty("map") MAP,
   @JsonProperty("viewport") VIEWPORT,
   @JsonProperty("auto") AUTO
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Layout(
        @JsonProperty("icon-image") val iconImage: Any? = null, //List<String> or String>
        @JsonProperty("icon-anchor") val iconAnchor: Anchor? = null,
        @JsonProperty("icon-allow-overlap") val iconAllowOverlap: Boolean? = null,
        @JsonProperty("icon-ignore-placement") val iconIgnorePlacement: Boolean? = null,
        @JsonProperty("icon-keep-upright") val iconKeepUpright: Boolean? = null,
        @JsonProperty("icon-rotate") val iconRotate: Any? = null,
        @JsonProperty("icon-size") val iconSize: Float? = null,
        @JsonProperty("symbol-spacing") val symbolSpacing: Float? = null,
        @JsonProperty("icon-rotation-alignment") val iconRotationAlignment: IconRotationAlignment? = null,
        @JsonProperty("text-font") val textFont: List<Font>? = null,
        @JsonProperty("text-anchor") val textAnchor: Anchor? = null,
        @JsonProperty("text-justify") val textJustify: Anchor? = null,
        @JsonProperty("text-field") val textField: Any? = null,
        @JsonProperty("text-offset") val textOffset: List<Float>? = null,

        /**
         * Positive values indicate right and down, while negative values indicate left and up.
         */
        @JsonProperty("icon-offset") val iconOffset: List<Float>? = null,
        @JsonProperty("text-allow-overlap") val textAllowOverlap: Boolean? = null,
        @JsonProperty("text-ignore-placement") val textIgnorePlacement: Boolean? = null,
        @JsonProperty("text-max-width") val textMaxWidthEms: Float? = null,
        @JsonProperty("text-size") val textSize: Float? = null,
        @JsonProperty("text-padding") val textPadding: Float? = null,
        @JsonProperty("symbol-placement") val symbolPlacement: Placement? = null,
)

enum class Placement {
    @JsonProperty("point") POINT,
    @JsonProperty("line") LINE,
    @JsonProperty("line-center") LINE_CENTER,
}

enum class Anchor {
    @JsonProperty("center") CENTER,
    @JsonProperty("bottom-left") BOTTOM_LEFT,
    @JsonProperty("bottom-right") BOTTOM_RIGHT,
    @JsonProperty("bottom") BOTTOM,
    @JsonProperty("top-left") TOP_LEFT,
    @JsonProperty("top-right") TOP_RIGHT,
}

enum class Font {
    @JsonProperty("Roboto Bold") ROBOTO_BOLD,
    @JsonProperty("Roboto Italic") ROBOTO_ITALIC,
    @JsonProperty("Roboto Medium") ROBOTO_MEDIUM,
    @JsonProperty("Roboto Regular") ROBOTO_REGULAR
}

/**
 * https://docs.mapbox.com/mapbox-gl-js/style-spec/layers/
 */
enum class LayerType {
    @JsonProperty("background") BACKGROUND,
    @JsonProperty("fill") FILL,
    @JsonProperty("line") LINE,
    @JsonProperty("symbol") SYMBOL,
    @JsonProperty("circle") CIRCLE,
    @JsonProperty("heatmap") HEATMAP,
    @JsonProperty("fill-extrusion") FILL_EXTRUSION,
    @JsonProperty("raster") RASTER,
    @JsonProperty("hillshade") HILLSHADE,
    @JsonProperty("sky") SKY,
}

data class Source(
        val type: SourceType = SourceType.VECTOR,
        @JsonProperty("url") val tileJsonUrl: String // "https://localhost:9000/v1/tile_json"
) {
    companion object {
        const val SENC = "src_senc"
    }
}

enum class SourceType {
    @JsonProperty("vector")
    VECTOR,
}