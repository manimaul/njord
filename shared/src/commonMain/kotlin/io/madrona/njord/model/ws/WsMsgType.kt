package io.madrona.njord.model.ws

import kotlinx.serialization.Serializable

@Serializable
sealed class WsMsg {

    @Serializable
    data class Error(
        val isFatal: Boolean,
        val message: String,
    ) : WsMsg()

    @Serializable
    data class Info(
        val feature: Long,
        val totalFeatures: Long,
        val chart: Int,
        val totalCharts: Int,
    ) : WsMsg()

    @Serializable
    data class CompletionReport(
        val totalFeatureCount: Long,
        val totalChartCount: Int,
        val items: List<InsertItem>,
        val failedCharts: List<String>,
        val ms: Long,
    ) : WsMsg()

    @Serializable
    data class InsertItem(
        val chartName: String,
        val featureCount: Int,
    )

    @Serializable
    data class Extracting(
        val progress: Float,
    ) : WsMsg()

    @Serializable
    data object Idle : WsMsg()
}
