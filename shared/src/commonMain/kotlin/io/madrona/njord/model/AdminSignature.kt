package io.madrona.njord.model

import kotlinx.serialization.Serializable

@Serializable
data class AdminResponse(
    val signature: AdminSignature,
    val signatureEncoded: String,
)

@Serializable
data class AdminSignature(
    val date: String,
    val signature: String,
    val uuid: String,
    val expirationDate: String,
)
