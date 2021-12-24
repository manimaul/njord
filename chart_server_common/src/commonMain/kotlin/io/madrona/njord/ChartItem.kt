package io.madrona.njord

import kotlinx.serialization.*

@Serializable
data class ChartItem(
    val id: Long,
    val name: String,
)