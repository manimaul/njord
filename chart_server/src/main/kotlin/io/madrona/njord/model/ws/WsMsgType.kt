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
        val name: String,
        val layer: String,
        val featureCount: Int,
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
        val layerName: String,
        val chartName: String,
        val featureCount: Int,
    )
}


suspend fun WebSocketSession.sendMessage(data: WsMsg) {
    send(Singletons.objectMapper.writeValueAsString(data))
}