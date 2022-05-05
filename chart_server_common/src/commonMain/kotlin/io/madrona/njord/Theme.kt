package io.madrona.njord

import kotlinx.serialization.Serializable

@Serializable
enum class Theme {
    Day,
    Dusk,
    Dark;

    companion object {
        fun fromName(name: String?): Theme? {
            return values().firstOrNull {
                it.name.equals(name, true)
            }
        }
    }
}
