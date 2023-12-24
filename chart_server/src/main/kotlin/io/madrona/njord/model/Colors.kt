package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.MapSerializer
import io.madrona.njord.Singletons
import io.madrona.njord.layers.Color
import io.madrona.njord.layers.Theme


class ColorLibrary(
    private val objectMapper: ObjectMapper = Singletons.objectMapper
) {

    val colorMap: Colors by lazy {
        objectMapper.readValue(javaClass.getResourceAsStream("/colors.json"), Colors::class.java)
    }
}


@JsonInclude(JsonInclude.Include.ALWAYS)
data class Colors(
        @JsonSerialize(keyUsing = MapSerializer::class) val library: Map<Theme, Map<Color, String>>
)



fun colorFrom(color: Color, theme: Theme): String = Singletons.colorLibrary.colorMap.library[theme]!![color]
        ?: throw RuntimeException("color key not found ${color.name}")