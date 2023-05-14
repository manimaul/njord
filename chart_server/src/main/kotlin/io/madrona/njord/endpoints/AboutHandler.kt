package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.AboutJson
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.respondJson
import io.madrona.njord.geo.symbols.S57ObjectLibrary
import io.madrona.njord.model.ColorLibrary
import org.gdal.gdal.gdal

class AboutHandler(
    private val s57ObjectLibrary: S57ObjectLibrary = Singletons.s57ObjectLibrary,
    private val colorLibrary: ColorLibrary = Singletons.colorLibrary,
) : KtorHandler {
    override val route = "/v1/about/{path...}"

    override suspend fun handleGet(call: ApplicationCall) {
        val aboutPath = call.parameters.getAll("path")?.fold(StringBuilder()) { acc, s ->
            acc.append('/').append(s)
        }.toString()

        when (aboutPath) {
            "/version" -> call.respond(
                AboutJson(
                    version = "1.0",
                    gdalVersion = gdal.VersionInfo() ?: "NONE"
                )
            )

            "/s57objects" -> call.respondJson(s57ObjectLibrary.objects)
            "/s57attributes" -> call.respondJson(s57ObjectLibrary.attributes)
            "/expectedInput" -> call.respondJson(s57ObjectLibrary.expectedInput)
            "/colors" -> call.respondJson(colorLibrary.colorMap.library)
            else -> call.respond(HttpStatusCode.NotFound)
        }

    }
}