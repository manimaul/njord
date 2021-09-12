package io.madrona.njord.endpoints

import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorWebsocket
import io.madrona.njord.logger
import kotlinx.coroutines.*

class ChartWebSocketHandler(
    config: ChartsConfig = Singletons.config,
    private val scope: CoroutineScope = Singletons.ioScope
) : KtorWebsocket {
    private val log = logger()
    private val charDir = config.chartTempData
    override val route = "/v1/ws/enc_process"

    override suspend fun handle(ws: DefaultWebSocketServerSession) {
        log.info("ws uri = ${ws.call.url()}")
        log.info("ws query keys = ${ws.call.request.queryParameters.names()}")
        ws.send("1")
        ws.send("2")

        scope.launch {
            delay(5000)
            ws.close()
        }

        ws.send("3")

        for (frame in ws.incoming) {
            when (frame) {
                is Frame.Text -> {
                    log.info("ws received ${frame.readText()}")
                }
                else -> {}
            }
        }
    }
}