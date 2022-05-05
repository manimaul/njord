package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.MapSerializer
import io.madrona.njord.Singletons
import io.madrona.njord.Theme


class ColorLibrary(
    private val objectMapper: ObjectMapper = Singletons.objectMapper
) {

    val colorMap: Colors by lazy {
        objectMapper.readValue(javaClass.getResourceAsStream("/colors.json"), Colors::class.java)
    }
}


@JsonInclude(JsonInclude.Include.ALWAYS)
data class Colors(
        @JsonSerialize(keyUsing = MapSerializer::class) val library: Map<String, Map<String, String>>
) {
    fun legendFrom(theme: Theme) = when (theme) {
        Theme.Day -> library["DAY_BRIGHT"]!!
        Theme.Dusk -> library["DUSK"]!!
        Theme.Dark -> library["NIGHT"]!!
    }
}

fun Theme.from(key: String): String = Singletons.colorLibrary.colorMap.legendFrom(this)[key]
        ?: throw RuntimeException("color key not found $key")