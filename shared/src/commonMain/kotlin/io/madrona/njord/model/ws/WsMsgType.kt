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
        val num: Int,
        val total: Int,
        val message: String,
    ) : WsMsg()

    @Serializable
    data class CompletionReport(
        val totalFeatureCount: Int,
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
        val step: Int,
        val progress: Float,
        val steps: Int = 2,
    ) : WsMsg()
}
