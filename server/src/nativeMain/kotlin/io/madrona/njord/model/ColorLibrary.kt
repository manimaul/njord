package io.madrona.njord.model

import io.madrona.njord.Singletons.colorLibrary
import io.madrona.njord.util.resourceAsString
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToJsonElement
import kotlinx.serialization.json.JsonArray


class ColorLibrary {

    val colorMap: Colors by lazy {
        decodeFromString(resourceAsString("colors.json")!!)
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
            is CustomTheme -> Color.entries.map { ColorTuple(it, colorFrom(it, theme)) }
            ThemeMode.Day,
            ThemeMode.Dusk,
            ThemeMode.Night -> colorMap.library[theme]!!.map { ColorTuple(it.key, it.value) }
        }
    }

    fun colorsJson(theme: Theme) : JsonArray {
        return JsonArray(colors(theme).map { encodeToJsonElement(ColorTuple.serializer(), it) })

    }

    fun hasCustom(name: String) : Boolean = colorMap.custom.containsKey(name)
}


fun colorFrom(color: Color, theme: Theme): String = colorLibrary.colorFrom(color, theme)