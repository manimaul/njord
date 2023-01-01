package io.madrona.njord.ext

//import io.ktor.application.*
import io.ktor.http.*
//import io.ktor.http.cio.websocket.*
//import io.ktor.http.content.*
//import io.ktor.response.*
//import io.ktor.routing.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.madrona.njord.Singletons

sealed interface KtorBaseHandler {
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
            when (handler) {
                is KtorHandler -> {
                    get(handler.route) { handler.handleGet(call) }
                    post(handler.route) { handler.handlePost(call) }
                    put(handler.route) { handler.handlePut(call) }
                    patch(handler.route) { handler.handlePatch(call) }
                    delete(handler.route) { handler.handleDelete(call) }
                    head(handler.route) { handler.handleHead(call) }
                    options(handler.route) { handler.handleOptions(call) }
                }
                is KtorWebsocket -> {
                    webSocket(handler.route) {
                        handler.handle(this)
                    }
                }
            }
        }
        static("/") {
            staticRootFolder = Singletons.config.webStaticContent
            files(".")
        }
    }
}
