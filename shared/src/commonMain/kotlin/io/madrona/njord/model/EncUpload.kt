package io.madrona.njord.model

import kotlinx.serialization.Serializable


@Serializable
data class EncUpload(
    val files: List<String>,
    val uuid: String,
)
