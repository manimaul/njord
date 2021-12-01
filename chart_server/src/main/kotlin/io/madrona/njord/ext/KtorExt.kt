package io.madrona.njord.ext

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import io.madrona.njord.Singletons
import java.io.File

interface KtorBaseHandler {
    val route: String
}

private suspend fun handle(call: ApplicationCall) {
    call.respond(HttpStatusCode.MethodNotAllowed)
}

interface KtorHandler : KtorBaseHandler {
    suspend fun handleGet(call: ApplicationCall) = handle(call)
    suspend fun handlePost(call: ApplicationCall) = handle(call)
    suspend fun handlePut(call: ApplicationCall) = handle(call)
    suspend fun handlePatch(call: ApplicationCall) = handle(call)
    suspend fun handleDelete(call: ApplicationCall) = handle(call)
    suspend fun handleHead(call: ApplicationCall) = handle(call)
    suspend fun handleOptions(call: ApplicationCall) = handle(call)
}

interface KtorWebsocket : KtorBaseHandler {
    suspend fun handle(ws: DefaultWebSocketServerSession)
}

fun Application.addHandlers(vararg handlers: KtorBaseHandler) {
    routing {
        handlers.forEach { handler ->
            (handler as? KtorHandler)?.let {
                get(handler.route) { handler.handleGet(call) }
                post(handler.route) { handler.handlePost(call) }
                put(handler.route) { handler.handlePut(call) }
                patch(handler.route) { handler.handlePatch(call) }
                delete(handler.route) { handler.handleDelete(call) }
                head(handler.route) { handler.handleHead(call) }
                options(handler.route) { handler.handleOptions(call) }
            }
            (handler as? KtorWebsocket)?.let {
                webSocket(handler.route) {
                    handler.handle(this)
                }
            }
        }
        static("v1/app/{...}") {
            staticRootFolder = Singletons.config.webStaticContent
            default("index.html")
        }
        static("njord.js") {
            staticRootFolder = Singletons.config.webStaticContent
            default("njord.js")
        }
        static("njord.js.map") {
            staticRootFolder = Singletons.config.webStaticContent
            default("njord.js.map")
        }
    }
}