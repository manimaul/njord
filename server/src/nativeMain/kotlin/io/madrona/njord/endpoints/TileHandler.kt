package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.TileDao
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.letThree
import io.madrona.njord.geo.TileEncoder

class TileHandler(
    private val tileDao: TileDao = Singletons.tileDao
) : KtorHandler {
    override val route = "/v1/tile/{z}/{x}/{y}"

    override suspend fun handleGet(call: ApplicationCall) {
        val info = call.request.queryParameters["info"]?.toBoolean() ?: false
        letThree(
            call.parameters["x"]?.toIntOrNull(),
            call.parameters["y"]?.toIntOrNull(),
            call.parameters["z"]?.toIntOrNull(),
        ) { x, y, z ->
            if (info) {
                call.respond(tileDao.getTileInfo(z, x, y))
            } else {
                call.respondBytes(tileDao.getTile(z, x, y), ContentType.Application.ProtoBuf)
            }
        }
    }
}