package io.madrona.njord.model

import io.madrona.njord.geojson.Feature
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


@Serializable
data class Style(
    @SerialName("glyphs") val glyphsUrl: String,
    val layers: List<Layer>,
    val name: String,
    val sources: Map<String, Source>,
    @SerialName("sprite") val spriteUrl: String,
    val version: Int
)

@Serializable
data class Layer(
    val id: String,
    val paint: Paint? = null,
    val type: LayerType,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val source: String? = Source.SENC,
    @SerialName("source-layer") val sourceLayer: String? = null,
    val filter: JsonElement? = null,
    val layout: Layout? = null,
)

@Serializable
data class Paint(
    @SerialName("text-color") val textColor: JsonElement? = null,
    @SerialName("text-halo-color") val textHaloColor: String? = null,
    @SerialName("text-halo-width") val textHaloWidth: Float? = null,
    @SerialName("background-color") val backgroundColor: String? = null,
    @SerialName("background-opacity") val backgroundOpacity: Int? = null,
    @SerialName("fill-color") val fillColor: JsonElement? = null,
    @SerialName("fill-pattern") val fillPattern: JsonElement? = null, //List<String> or String>
    @SerialName("line-color") val lineColor: JsonElement? = null,
    @SerialName("line-width") val lineWidth: Float? = null,
    @SerialName("line-dasharray") val lineDashArray: List<Float>? = null,
    @SerialName("line-pattern") val linePattern: JsonElement? = null, //List<String> or String>
    @SerialName("circle-opacity") val circleOpacity: Float? = null,
    @SerialName("circle-color") val circleColor: JsonElement? = null,
    @SerialName("circle-stroke-color") val circleStrokeColor: JsonElement? = null,
    @SerialName("circle-radius") val circleRadius: Float? = null,
    @SerialName("circle-stroke-width") val circleStrokeWidth: Float? = null,
)

@Serializable
enum class IconRotationAlignment {
    @SerialName("map")
    MAP,

    @SerialName("viewport")
    VIEWPORT,

    @SerialName("auto")
    AUTO
}

@Serializable
data class Layout(
    @SerialName("icon-image") val iconImage: JsonElement? = null, //List<String> or String>
    @SerialName("icon-anchor") val iconAnchor: Anchor? = null,
    @SerialName("icon-allow-overlap") val iconAllowOverlap: Boolean? = null,
    @SerialName("icon-ignore-placement") val iconIgnorePlacement: Boolean? = null,
    @SerialName("icon-keep-upright") val iconKeepUpright: Boolean? = null,
    @SerialName("icon-rotate") val iconRotate: JsonElement? = null,
    @SerialName("icon-size") val iconSize: Float? = null,
    @SerialName("symbol-spacing") val symbolSpacing: Float? = null,
    @SerialName("icon-rotation-alignment") val iconRotationAlignment: JsonElement? = null,
    @SerialName("text-font") val textFont: List<Font>? = null,
    @SerialName("text-anchor") val textAnchor: Anchor? = null,
    @SerialName("text-justify") val textJustify: TextJustify? = null,
    @SerialName("text-field") val textField: JsonElement? = null,
    @SerialName("text-offset") val textOffset: JsonElement? = null,
    @SerialName("line-join") val lineJoin: LineJoin? = null,
    @SerialName("line-cap") val lineCap: LineCap? = null,

    /**
     * Positive values indicate right and down, while negative values indicate left and up.
     */
    @SerialName("icon-offset") val iconOffset: JsonElement? = null,
    @SerialName("text-allow-overlap") val textAllowOverlap: Boolean? = null,
    @SerialName("text-ignore-placement") val textIgnorePlacement: Boolean? = null,
    @SerialName("text-max-width") val textMaxWidthEms: Float? = null,
    @SerialName("text-size") val textSize: Float? = null,
    @SerialName("text-padding") val textPadding: Float? = null,
    @SerialName("symbol-placement") val symbolPlacement: Placement? = null,
)

@Serializable
enum class LineCap {
    @SerialName("butt")
    BUTT,

    @SerialName("round")
    ROUND,

    @SerialName("square")
    SQUARE,
}

@Serializable
enum class LineJoin{
    @SerialName("bevel")
    BEVEL,

    @SerialName("round")
    ROUND,

    @SerialName("miter")
    MITER,
}

@Serializable
enum class Placement {
    @SerialName("point")
    POINT,

    @SerialName("line")
    LINE,

    @SerialName("line-center")
    LINE_CENTER,
}

@Serializable
enum class TextJustify {
    @SerialName("auto")
    AUTO,

    @SerialName("left")
    LEFT,

    @SerialName("center")
    CENTER,

    @SerialName("right")
    RIGHT,
}

@Serializable
enum class Anchor {
    @SerialName("center")
    CENTER,

    @SerialName("bottom-left")
    BOTTOM_LEFT,

    @SerialName("bottom-right")
    BOTTOM_RIGHT,

    @SerialName("bottom")
    BOTTOM,

    @SerialName("top-left")
    TOP_LEFT,

    @SerialName("top-right")
    TOP_RIGHT,
}

@Serializable
enum class Font {
    @SerialName("Roboto Bold")
    ROBOTO_BOLD,

    @SerialName("Roboto Italic")
    ROBOTO_ITALIC,

    @SerialName("Roboto Medium")
    ROBOTO_MEDIUM,

    @SerialName("Roboto Regular")
    ROBOTO_REGULAR
}

/**
 * https://docs.mapbox.com/mapbox-gl-js/style-spec/layers/
 */
@Serializable
enum class LayerType {
    @SerialName("background")
    BACKGROUND,

    @SerialName("fill")
    FILL,

    @SerialName("line")
    LINE,

    @SerialName("symbol")
    SYMBOL,

    @SerialName("circle")
    CIRCLE,

    @SerialName("heatmap")
    HEATMAP,

    @SerialName("fill-extrusion")
    FILL_EXTRUSION,

    @SerialName("raster")
    RASTER,

    @SerialName("hillshade")
    HILLSHADE,

    @SerialName("sky")
    SKY,
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Source(

    @EncodeDefault
    val type: SourceType = SourceType.VECTOR,

    @SerialName("url") val tileJsonUrl: String? = null, // "https://localhost:9000/v1/tile_json"

    val data: Feature? = null,
) {
    companion object {
        const val SENC = "src_senc"
    }
}

@Serializable
enum class SourceType {
    @SerialName("vector")
    VECTOR,
    @SerialName("geojson")
    GEOJSON,
}