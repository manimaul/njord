package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AdminResponse(
    val signature: AdminSignature,
    val signatureEncoded: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AdminSignature(
    val date: String,
    val signature: String,
    val uuid: String,
    val expirationDate: String,
)
