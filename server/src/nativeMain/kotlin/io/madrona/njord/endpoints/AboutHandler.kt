package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.geo.symbols.S57ObjectLibrary
import io.madrona.njord.model.AboutJson
import io.madrona.njord.model.ColorLibrary

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
            "/version" -> call.respond(about())
            "/s57objects" -> call.respond(s57ObjectLibrary.objects)
            "/s57attributes" -> call.respond(s57ObjectLibrary.attributes)
            "/expectedInput" -> call.respond(s57ObjectLibrary.expectedInput)
            "/colors" -> call.respond(colorLibrary.colorMap.library)
            "/colors/custom" -> call.respond(colorLibrary.colorMap.custom)
            else -> call.respond(HttpStatusCode.NotFound)
        }

    }

    private fun about(): AboutJson {

        return AboutJson(
            version = "", //version,
            gdalVersion = "", //gdal.VersionInfo() ?: "NONE",
            gitHash = "", //gitHash,
            gitBranch = "", //gitBranch,
            buildDate = "", //buildDate,
        )

    }
}
