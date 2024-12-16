package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonProperty

enum class Depth {
    @JsonProperty("fathoms") FATHOMS,
    @JsonProperty("meters") METERS,
    @JsonProperty("feet") FEET
}