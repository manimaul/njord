package io.madrona.njord

data class ChartItem(
    val id: Long,
    val name: String,
    val featureCount: Int,
)

data class ChartCatalog(
    val totalChartCount: Int,
    val nextId: Long?,
    val page: List<ChartItem>,
)