package io.madrona.njord.endpoints

import com.willkamp.vial.api.EndPointHandler
import com.willkamp.vial.api.Request
import io.madrona.njord.model.About
import org.gdal.gdal.gdal

class AboutHandler : EndPointHandler {
    override val route = "/v1/about"

    override fun handle(request: Request) {
        request.respondWith {
            it.setBodyJson(
                About(
                    version = "1.0",
                    gdalVersion = gdal.VersionInfo() ?: "NONE"
                )
            )
        }
    }
}