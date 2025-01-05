package io.madrona.njord.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AdminResponse(
    val signature: AdminSignature,
    val signatureEncoded: String,
)

@Serializable
data class AdminSignature(
    val date: Instant,
    val signature: String,
    val uuid: String,
    val expirationDate: Instant,
)
