package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.ALWAYS)
data class Style(
        @JsonProperty("glyphys") val glyphsUrl: String,
       val layers: List<Layer> = listOf(),
       val name: String,
       val sources: Map<String, Source> = mapOf(
               "src_senc" to Source()
       ),
        @JsonProperty("sprite") val spriteUrl: String, // "http://localhost:9000/res/sprites/rastersymbols-day"
       val version: Int = 8,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class Layer(
        val id: String,
        val source: String,
        val type: String,
        @JsonProperty("source-layer") val sourceLayer: String,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class Paint(
        @JsonProperty("background-color") val backgroundColor: String,
        @JsonProperty("background-opacity") val backgroundOpacity: Int,
)

enum class LayerType {
    @JsonProperty("background") BACKGROUND,
    @JsonProperty("fill") FILL,
    @JsonProperty("line") LINE,
    @JsonProperty("symbol") SYMBOL
}

data class Source(
        val type: SourceType = SourceType.VECTOR,
        val url: String = "https://localhost:9000/v1/tileJson"
)

enum class SourceType {
    @JsonProperty("vector") VECTOR,
}