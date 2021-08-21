package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonProperty

enum class StyleColor {
    @JsonProperty("day") DAY,
    @JsonProperty("dusk") DUSK,
    @JsonProperty("dark") DARK;
}
