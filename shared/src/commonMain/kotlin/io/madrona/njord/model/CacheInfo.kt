package io.madrona.njord.model

import kotlinx.serialization.Serializable

@Serializable
data class CacheInfo(
    val connections: Int,
    val currentItemCount: Long
)

