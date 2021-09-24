package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.db.ChartDao
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.ChartInsert

class ChartHandler(
    private val dao: ChartDao = Singletons.chartDao
) : KtorHandler {
    override val route = "/v1/chart"
    override fun method() = HttpMethod.Post

    override suspend fun handle(call: ApplicationCall) {
        val chart = call.receive<ChartInsert>()
        dao.insertAsync(chart).await()?.let {
            call.respond(it)
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
}