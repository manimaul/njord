package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.letThree
import io.madrona.njord.geo.TileEncoder

class TileHandler(
    private val chartsConfig: ChartsConfig = Singletons.config
) : KtorHandler {
    override val route = "/v1/tile/{z}/{x}/{y}"

    override suspend fun handleGet(call: ApplicationCall) {
        val info = call.request.queryParameters["info"]?.toBoolean() ?: false
        letThree(
            call.parameters["x"]?.toIntOrNull(),
            call.parameters["y"]?.toIntOrNull(),
            call.parameters["z"]?.toIntOrNull(),
        ) { x, y, z ->
            TileEncoder(x, y, z)
                .addCharts(info)
                .apply {
                    if (chartsConfig.debugTile) {
                        addDebug()
                    }
                }
        }?.let {
            if (info) {
                call.respond(it.infoJson())
            } else {
                call.respondBytes(it.encode(), ContentType.Application.ProtoBuf)
            }
        } ?: call.respond(HttpStatusCode.NotFound)
    }
}