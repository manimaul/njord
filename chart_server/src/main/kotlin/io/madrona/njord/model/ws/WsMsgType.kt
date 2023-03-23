package io.madrona.njord.model.ws

import com.fasterxml.jackson.annotation.JsonInclude
import io.ktor.websocket.*
import io.madrona.njord.Singletons

/*
s57 1 of 10
US5WA26M
3847 features
layer: SLOGRD
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed class WsMsg {

    val type: String = javaClass.simpleName

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Error(
        val isFatal: Boolean,
        val message: String,
    ) : WsMsg()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Info(
        val num: Int,
        val total: Int,
        val message: String,
    ) : WsMsg()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class CompletionReport(
        val totalFeatureCount: Int,
        val totalChartCount: Int,
        val items: List<InsertItem>,
        val failedCharts: List<String>,
        val ms: Long,
    ) : WsMsg()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class InsertItem(
        val chartName: String,
        val featureCount: Int,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Extracting(
        val step: Int,
        val progress: Float,
        val steps: Int = 2,
    ) : WsMsg()
}


suspend fun WebSocketSession.sendMessage(data: WsMsg) {
    send(Singletons.objectMapper.writeValueAsString(data))
}