package io.madrona.njord.endpoints

import com.willkamp.vial.api.EndPointHandler
import com.willkamp.vial.api.Request
import io.madrona.njord.ChartsConfig
import io.madrona.njord.model.TileJson

class TileJsonHandler(
        private val config: ChartsConfig,
) : EndPointHandler {
    override val route = "/v1/tile_json"

    override fun handle(request: Request) {
        request.respondWith {
            it.setBodyJson(
                    TileJson(
                          tiles = listOf(
                                  "${config.externalBaseUrl}/v1/tile/{z}/{x}/{y}"
                          )
                    )
            )
        }
    }
}
