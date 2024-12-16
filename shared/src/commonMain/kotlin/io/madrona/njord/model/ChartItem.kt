package io.madrona.njord.model

import kotlinx.serialization.Serializable

@Serializable
data class ChartItem(
    val id: Long,
    val name: String,
)

@Serializable
data class ChartCatalog(
    val totalChartCount: Int,
    val nextId: Long?,
    val page: List<ChartItem>,
)