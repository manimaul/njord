package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.response.*
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.About
import org.gdal.gdal.gdal

class AboutHandler : KtorHandler {
    override val route = "/v1/about"

    override suspend fun handle(call: ApplicationCall) {
        call.respond(
            About(
                version = "1.0",
                gdalVersion = gdal.VersionInfo() ?: "NONE"
            )
        )
    }
}