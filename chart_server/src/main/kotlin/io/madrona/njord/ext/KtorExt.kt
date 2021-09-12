package io.madrona.njord.ext

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*

interface KtorBaseHandler {
    val route: String
}

interface KtorHandler : KtorBaseHandler {
    fun method() = HttpMethod.Get
    suspend fun handle(call: ApplicationCall)
}

interface KtorWebsocket : KtorBaseHandler {
    suspend fun handle(ws: DefaultWebSocketServerSession)
}

fun Application.addHandlers(vararg handlers: KtorBaseHandler) {
    routing {
        handlers.forEach { handler ->
            (handler as? KtorHandler)?.let {
                when (handler.method()) {
                    HttpMethod.Get -> get(handler.route) { handler.handle(call) }
                    HttpMethod.Post -> post(handler.route) { handler.handle(call) }
                    HttpMethod.Put -> put(handler.route) { handler.handle(call) }
                    HttpMethod.Patch -> patch(handler.route) { handler.handle(call) }
                    HttpMethod.Delete -> delete(handler.route) { handler.handle(call) }
                    HttpMethod.Head -> head(handler.route) { handler.handle(call) }
                    HttpMethod.Options -> options(handler.route) { handler.handle(call) }
                    else -> {}
                }
            }
            (handler as? KtorWebsocket)?.let {
                webSocket(handler.route) {
                    handler.handle(this)
                }
            }
        }
    }
}