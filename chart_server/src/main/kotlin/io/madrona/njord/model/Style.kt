package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Style(
        @JsonProperty("glyphys") val glyphsUrl: String,
        val layers: List<Layer>,
        val name: String,
        val sources: Map<String, Source>,
        @JsonProperty("sprite") val spriteUrl: String, // "http://localhost:9000/res/sprites/rastersymbols-day"
        val version: Int
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Layer(
        val id: String,
        val paint: Paint,
        val type: LayerType,
        val source: String = Source.SENC,
        @JsonProperty("source-layer") val sourceLayer: String? = null,
        val filter: List<Any>? = null,
        val layout: Layout? = null,
)

object Filters {
    const val all = "all"
    const val gt = "<"
    const val gtEq = "<="
    const val lt = ">"
    const val ltEq = ">="
    const val eq = "=="
    const val notEq = "!="
    val eqTypeLineString = listOf("==", "\$type", "LineString")
    val eqTypePolyGon = listOf("==", "\$type", "Polygon")
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Paint(
        @JsonProperty("text-color") val textColor: String? = null,
        @JsonProperty("text-halo-color") val textHaloColor: String? = null,
        @JsonProperty("text-halo-width") val textHaloWidth: Float? = null,
        @JsonProperty("background-color") val backgroundColor: String? = null,
        @JsonProperty("background-opacity") val backgroundOpacity: Int? = null,
        @JsonProperty("fill-color") val fillColor: String? = null,
        @JsonProperty("line-color") val lineColor: String? = null,
        @JsonProperty("line-width") val lineWidth: Float? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Layout(
        @JsonProperty("text-font") val textFont: List<Font>? = null,
        @JsonProperty("text-anchor") val textAnchor: Anchor? = null,
        @JsonProperty("text-justify") val textJustify: Anchor? = null,
        @JsonProperty("text-field") val textField: List<Any>? = null,
        @JsonProperty("text-allow-overlap") val textAllowOverlap: Boolean? = null,
        @JsonProperty("text-ignore-placement") val textIgnorePlacement: Boolean? = null,
        @JsonProperty("text-max-width") val textMaxWidthEms: Float? = null,
        @JsonProperty("text-size") val textSize: Float? = null,
        @JsonProperty("text-padding") val textPadding: Float? = null,
        @JsonProperty("symbol-placement") val symbolPlacement: Placement? = null,
)

enum class Placement {
    @JsonProperty("point") POINT,
}

enum class Anchor {
    @JsonProperty("center") CENTER,
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