package io.madrona.njord.endpoints

import com.willkamp.vial.api.EndPointHandler
import com.willkamp.vial.api.Request
import com.willkamp.vial.api.VialConfig
import io.madrona.njord.model.About
import io.madrona.njord.model.tileJson
import org.gdal.gdal.gdal

class TileJsonHandler(
        private val vialConfig: VialConfig
) : EndPointHandler {
    override val route = "/v1/tileJson"

    override fun handle(request: Request) {
        request.respondWith {
            it.setBodyJson(tileJson("${"127.0.0.1"}:${vialConfig.port}"))
        }
    }
}