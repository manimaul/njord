package io.madrona.njord.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
enum class Depth {
    @SerialName("fathoms") FATHOMS,
    @SerialName("meters") METERS,
    @SerialName("feet") FEET
}