package io.madrona.njord.endpoints

import File
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorWebsocket
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.util.logger
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class ChartWebSocketHandler(
    private val statusFile: File = Singletons.ingestStatusFile,
) : KtorWebsocket {
    private val log = logger()
    override val route = "/v1/ws/enc_process"

    override suspend fun handle(ws: DefaultWebSocketServerSession) = ws.call.requireSignature {
        val idleJson = Json.encodeToString(WsMsg.serializer(), WsMsg.Idle)

        // Send current status immediately on connect
        var lastContent = statusFile.readContents().takeIf { it.isNotEmpty() } ?: idleJson
        ws.send(lastContent)

        // Poll and broadcast changes
        while (true) {
            delay(500)
            val content = statusFile.readContents().takeIf { it.isNotEmpty() } ?: idleJson
            if (content != lastContent) {
                lastContent = content
                ws.send(content)
            }
        }
    }
}
