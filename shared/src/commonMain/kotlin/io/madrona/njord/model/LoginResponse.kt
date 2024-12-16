package io.madrona.njord.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val id: Long,
    val hashOfHash: String,
    val salt: String,
    val expires: Instant,
)
