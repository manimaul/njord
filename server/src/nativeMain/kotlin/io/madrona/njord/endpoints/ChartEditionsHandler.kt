package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.db.ChartDao
import io.madrona.njord.ext.KtorHandler

/**
 * Bulk chart edition listing, consumed by the `enc_cron` job to decide which NOAA cells are out
 * of date without needing database credentials of its own.
 *
 * Deliberately unpaginated, unlike [ChartCatalogHandler] - the caller needs the whole set to diff
 * against NOAA's product catalog, and the whole set is ~7k entries.
 */
class ChartEditionsHandler(
    private val dao: ChartDao = ChartDao()
) : KtorHandler {
    override val route = "/v1/chart_editions"

    override suspend fun handleGet(call: ApplicationCall) {
        dao.editionsAsync()?.let {
            call.respond(it)
        } ?: call.respond(HttpStatusCode.InternalServerError)
    }
}
