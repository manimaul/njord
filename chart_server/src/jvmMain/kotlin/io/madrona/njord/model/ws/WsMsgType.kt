package io.madrona.njord.model.ws

import io.ktor.websocket.*
import kotlinx.serialization.json.Json


suspend fun WebSocketSession.sendMessage(data: WsMsg) {
    send(Json.encodeToString(WsMsg.serializer(), data))
}