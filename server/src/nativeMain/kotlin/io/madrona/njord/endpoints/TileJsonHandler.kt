package io.madrona.njord.endpoints

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.externalBaseUrl
import io.madrona.njord.model.TileJson

class TileJsonHandler : KtorHandler {
    override val route = "/v1/tile_json"

    override suspend fun handleGet(call: ApplicationCall) {
        call.respond(
            TileJson(
                tiles = listOf(
                    "${call.externalBaseUrl()}/v1/tile/{z}/{x}/{y}"
                )
            )
        )
    }
}
