package io.madrona.njord.model

import kotlinx.serialization.Serializable


@Serializable
data class EncUpload(
    val zipFiles: List<String>,
)
