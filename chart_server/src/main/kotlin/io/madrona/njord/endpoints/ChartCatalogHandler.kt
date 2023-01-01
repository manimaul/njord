package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.db.ChartDao
import io.madrona.njord.ext.KtorHandler

class ChartCatalogHandler(
    private val dao: ChartDao = ChartDao()
) : KtorHandler {
    override val route = "/v1/chart_catalog"

    override suspend fun handleGet(call: ApplicationCall) {
        dao.listAsync().await()?.let { chartList ->
            call.respond(chartList)
        } ?: call.respond(HttpStatusCode.NotFound)
    }
}
