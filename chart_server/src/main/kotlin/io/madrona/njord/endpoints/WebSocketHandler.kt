package io.madrona.njord.endpoints

import com.willkamp.vial.api.WebSocket
import com.willkamp.vial.api.WebSocketHandler
import io.madrona.njord.logger

class ChartWebSocketHandler : WebSocketHandler {
    val log = logger()
    override val route = "/v1/ws/enc_process"

    override fun handle(ws: WebSocket) {
        log.info("ws uri = ${ws.uri}")
        log.info("ws query keys = ${ws.queryKeys()}")
        ws.receiveText {
            log.info("ws received $it")
        }
        ws.sendText("1")
        ws.sendText("2")
        ws.sendText("3")
    }
}