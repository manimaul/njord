package io.madrona.njord.endpoints

import io.ktor.server.request.*
import io.ktor.server.util.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.madrona.njord.ext.KtorWebsocket
import io.madrona.njord.ext.letTwo
import io.madrona.njord.ingest.ChartIngest
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.model.ws.sendMessage
import io.madrona.njord.util.logger

class ChartWebSocketHandler(
) : KtorWebsocket {
    private val log = logger()
    override val route = "/v1/ws/enc_process"

    override suspend fun handle(ws: DefaultWebSocketServerSession) = ws.call.requireSignature {
        log.info("ws uri = ${ws.call.url()}")
        log.info("ws query keys = ${ws.call.request.queryParameters.names()}")
        letTwo(
            ws.call.request.queryParameters["uuid"],
            ws.call.request.queryParameters.getAll("file")
        ) { uuid, files ->
            ChartIngest(webSocketSession = ws).ingest(EncUpload(files, uuid))
        } ?: run {
            log.error("ws invalid query params ${ws.call.request.queryString()}")
            ws.sendMessage(
                WsMsg.Error(
                    message = "invalid query params",
                    isFatal = true,
                )
            )
            ws.close(CloseReason(CloseReason.Codes.NORMAL, "uuid and or files not provided"))
        }
    }
}
