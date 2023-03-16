package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.madrona.njord.db.ChartDao
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.ChartInsert

class ChartHandler(
    private val dao: ChartDao = ChartDao()
) : KtorHandler {
    override val route = "/v1/chart"

    override suspend fun handleGet(call: ApplicationCall) {
        call.request.queryParameters["id"]?.toLongOrNull()?.let {
            dao.findAsync(it).await()?.let { chart ->
                call.respond(chart)
            } ?: call.respond(HttpStatusCode.NotFound)
        } ?: call.respond(HttpStatusCode.BadRequest)
    }

    override suspend fun handlePost(call: ApplicationCall) = call.requireSignature {
        val chart = call.receive<ChartInsert>()
        dao.insertAsync(chart).await()?.let {
            call.respond(it)
        } ?: call.respond(HttpStatusCode.BadRequest)
    }

    override suspend fun handleDelete(call: ApplicationCall) = call.requireSignature {
        when (
            call.request.queryParameters["id"]?.toLongOrNull()?.let {
                dao.deleteAsync(it).await()
            }
        ) {
            true -> call.respond(HttpStatusCode.Accepted)
            false -> call.respond(HttpStatusCode.NoContent)
            null -> call.respond(HttpStatusCode.BadRequest)
        }
    }
}
