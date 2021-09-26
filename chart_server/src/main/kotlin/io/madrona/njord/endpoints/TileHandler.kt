package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.madrona.njord.db.GeoJsonDao
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.letThree

class TileHandler(
    private val geoJsonDao: GeoJsonDao = GeoJsonDao()
) : KtorHandler {
    override val route = "/v1/tile/{z}/{x}/{y}"

    override suspend fun handleGet(call: ApplicationCall) {
        letThree(
            call.parameters["z"]?.toIntOrNull(),
            call.parameters["x"]?.toIntOrNull(),
            call.parameters["y"]?.toIntOrNull()) { z, x, y ->
            geoJsonDao.fetchTileAsync(z, x, y).await()
        }?.let {
            call.respondBytes(it, ContentType.Application.ProtoBuf)
        } ?: call.respond(HttpStatusCode.NotFound)
    }
}