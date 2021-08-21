package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
data class About(
        val version: String,
        val gdalVersion: String,
)