package io.madrona.njord.model

import kotlinx.serialization.Serializable

@Serializable
data class EncUrlRequest(
    val url: String,
)
