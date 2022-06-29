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
        letThree(
            call.parameters["x"]?.toIntOrNull(),
            call.parameters["y"]?.toIntOrNull(),
            call.parameters["z"]?.toIntOrNull(),
        ) { x, y, z ->
            TileEncoder(x, y, z)
                .addCharts()
                .apply {
                    if (chartsConfig.debugTile) {
                        addDebug()
                    }
                }
                .encode()
        }?.let {
            call.respondBytes(it, ContentType.Application.ProtoBuf)
        } ?: call.respond(HttpStatusCode.NotFound)
    }
}