package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.MapSerializer
import io.madrona.njord.objectMapper

object ColorLibrary {
    val colorMap: Colors by lazy {
        objectMapper.readValue(javaClass.getResourceAsStream("/colors.json"), Colors::class.java)
    }

    const val black = "#000"
    const val white = "#FFF"
}

@JsonInclude(JsonInclude.Include.ALWAYS)
data class Colors(
        @JsonSerialize(keyUsing = MapSerializer::class) val library: Map<String, Map<String, String>>
) {
    fun legendFrom(styleColor: StyleColor) = when (styleColor) {
        StyleColor.DAY -> library["DAY_BRIGHT"]!!
        StyleColor.DUSK -> library["DUSK"]!!
        StyleColor.DARK -> library["NIGHT"]!!
    }
}

fun StyleColor.from(key: String): String = ColorLibrary.colorMap.legendFrom(this)[key]
        ?: throw RuntimeException("color key not found $key")