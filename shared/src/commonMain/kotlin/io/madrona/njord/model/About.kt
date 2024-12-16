package io.madrona.njord.model

import kotlinx.serialization.Serializable

@Serializable
data class AboutJson(
    val version: String,
    val gdalVersion: String,
    val gitHash: String,
    val gitBranch: String,
    val buildDate: String,
)
