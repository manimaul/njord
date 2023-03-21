package io.madrona.njord.model.ws

import com.fasterxml.jackson.annotation.JsonInclude
import io.ktor.websocket.*
import io.madrona.njord.Singletons

@JsonInclude(JsonInclude.Include.NON_NULL)
sealed class WsMsg {

    val type: String = javaClass.simpleName

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class FatalError(
        val message: String
    ) : WsMsg()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Info(
        val message: String
    ) : WsMsg()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class InsertionStatus(
        val chartName: String,
        val message: String,
        val isError: Boolean = false
    ) : WsMsg()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Insertion(
        val chartName: String,
        val featureCount: Int
    ) : WsMsg()
}


suspend fun WebSocketSession.sendMessage(data: WsMsg) {
    send(Singletons.objectMapper.writeValueAsString(data))
}