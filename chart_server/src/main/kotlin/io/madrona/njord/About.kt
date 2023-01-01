package io.madrona.njord

import kotlinx.serialization.*

@Serializable
data class AboutJson(
    val version: String,
    val gdalVersion: String,
)