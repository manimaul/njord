package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.MapSerializer
import io.madrona.njord.Singletons
import io.madrona.njord.layers.Color
import io.madrona.njord.layers.CustomTheme
import io.madrona.njord.layers.Theme
import io.madrona.njord.layers.ThemeMode


class ColorLibrary(
    private val objectMapper: ObjectMapper = Singletons.objectMapper
) {

    val colorMap: Colors by lazy {
        objectMapper.readValue(javaClass.getResourceAsStream("/colors.json"), Colors::class.java)
    }

    fun colorFrom(color: Color, theme: Theme): String {
        return when (theme) {
            is CustomTheme -> {
                colorMap.custom[theme.name]?.let {
                    it[theme.mode]
                }?.let {
                    it[color]
                } ?: colorMap.library[theme.mode]!![color]
                ?: throw RuntimeException("color key not found ${color.name}")
            }

            ThemeMode.Day,
            ThemeMode.Dusk,
            ThemeMode.Night ->
                colorMap.library[theme]!![color]
                    ?: throw RuntimeException("color key not found ${color.name}")
        }
    }

    fun colors(theme: Theme) : List<ColorTuple> {
        return when(theme) {
            is CustomTheme -> Color.values().map { ColorTuple(it, colorFrom(it, theme)) }
            ThemeMode.Day,
            ThemeMode.Dusk,
            ThemeMode.Night -> colorMap.library[theme]!!.map { ColorTuple(it.key, it.value) }
        }
    }

    fun hasCustom(name: String) : Boolean = colorMap.custom.containsKey(name)
}

data class ColorTuple(
    val color: Color,
    val hex: String,
)


@JsonInclude(JsonInclude.Include.ALWAYS)
data class Colors(
    @JsonSerialize(keyUsing = MapSerializer::class) val library: Map<ThemeMode, Map<Color, String>>,
    @JsonSerialize(keyUsing = MapSerializer::class) val custom: Map<String, Map<ThemeMode, Map<Color, String>>>
)

fun colorFrom(color: Color, theme: Theme) = Singletons.colorLibrary.colorFrom(color, theme)