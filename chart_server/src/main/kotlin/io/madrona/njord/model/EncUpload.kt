package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude


@JsonInclude(JsonInclude.Include.ALWAYS)
data class EncUpload(
        val files: List<String>,
        val uuid: String,
)
