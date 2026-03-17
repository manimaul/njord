package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.buildSectorSvg
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.fromString
import io.madrona.njord.model.Color
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.model.colorFrom

class SvgLightSectorHandler : KtorHandler {

    override val route = "/v1/sector_svg/{theme}/{sectr1}/{sectr2}/{color}"

    override suspend fun handleGet(call: ApplicationCall) {
        val theme = fromString<ThemeMode>(call.parameters["theme"]) ?: ThemeMode.Day
        val sectr1 = call.parameters["sectr1"]?.toDoubleOrNull()
        val sectr2 = call.parameters["sectr2"]?.toDoubleOrNull()
        val color = call.parameters["color"]?.let { fromString<Color>(it) }

        if (sectr1 == null || sectr2 == null || color == null) {
            call.respond(HttpStatusCode.BadRequest, "SECTR1 and SECTR2 are required numeric parameters")
            return
        }

        val colorHex = colorFrom(color, theme)
        val radius = when(color) {
            Color.LITRD -> 68.0
            Color.LITGN -> 74.0
            else -> 80.0
        }
        val lineColorHex = colorFrom(Color.CHGRD, theme)

        val svg = buildSectorSvg(sectr1, sectr2, colorHex, lineColorHex, radius)
        call.respondText(svg, ContentType.parse("image/svg+xml"))
    }
}
