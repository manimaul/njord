package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.externalBaseUrl
import io.madrona.njord.ext.fromString
import io.madrona.njord.layers.*
import io.madrona.njord.model.*
import kotlinx.serialization.json.Json


class StyleHandler(
    private val layerFactory: LayerFactory = Singletons.layerFactory,
    private val colorLibrary: ColorLibrary = Singletons.colorLibrary,
) : KtorHandler {
    override val route = "/v1/style/{depth}/{theme}"
    private val styleJson = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    override suspend fun handleGet(call: ApplicationCall) {
        fromString<Depth>(call.parameters["depth"])?.let { depth ->
            fromString<ThemeMode>(call.parameters["theme"])?.let { themeMode ->
                val theme = call.request.queryParameters["c"]?.takeIf {
                    colorLibrary.hasCustom(it)
                }?.let {
                    CustomTheme(themeMode, it)
                } ?: themeMode
                val name = "${themeMode.name.lowercase()}_simplified"
                call.respondText(styleString(call.externalBaseUrl(), name, depth, theme), ContentType.Application.Json, HttpStatusCode.OK)
            }
        } ?: call.respond(HttpStatusCode.NotFound, Any())
    }

    private fun styleString(baseUrl: String, name: String, depth: Depth, theme: Theme): String {
        return styleJson.encodeToString(
            Style.serializer(),
            Style(
                name = name,
                glyphsUrl = "$baseUrl/v1/content/fonts/{fontstack}/{range}.pbf",
                spriteUrl = "$baseUrl/v1/content/sprites/$name",
                sources = mapOf(
                    Source.SENC to Source(
                        type = SourceType.VECTOR,
                        tileJsonUrl = "$baseUrl/v1/tile_json"
                    )
                ),
                layers = layerFactory.layers(LayerableOptions(depth, theme)),
                version = 8
            )
        )
    }
}
