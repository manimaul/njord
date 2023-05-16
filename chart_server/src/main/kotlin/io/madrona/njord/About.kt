package io.madrona.njord

data class AboutJson(
    val version: String,
    val gdalVersion: String,
    val gitHash: String,
    val gitBranch: String,
    val buildDate: String,
)