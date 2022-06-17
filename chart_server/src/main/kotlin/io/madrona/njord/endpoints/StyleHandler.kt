package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.Theme
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.letFromStrings
import io.madrona.njord.layers.*
import io.madrona.njord.model.*
import io.madrona.njord.util.SpriteSheet

class StyleHandler(
    private val spriteSheet: SpriteSheet = Singletons.spriteSheet,
    private val config: ChartsConfig = Singletons.config,
    private val layerFactory: LayerFactory = LayerFactory()
) : KtorHandler {
    override val route = "/v1/style/{theme}/{depth}"

    override suspend fun handleGet(call: ApplicationCall) {
        letFromStrings(call.parameters["theme"], call.parameters["depth"]) { theme: Theme, depth: Depth ->
            val name = "${theme.name.lowercase()}-${depth.name.lowercase()}"
            call.respond(
                Style(
                    name = name,
                    glyphsUrl = "${config.externalBaseUrl}/v1/content/fonts/{fontstack}/{range}.pbf",
                    spriteUrl = "${config.externalBaseUrl}/v1/content/sprites/${spriteSheet.resNameBase}",
                    sources = mapOf(
                        Source.SENC to Source(
                            type = SourceType.VECTOR,
                            tileJsonUrl = "${config.externalBaseUrl}/v1/tile_json"
                        )
                    ),

                    layers = layerFactory.layers(LayerableOptions(depth)),
                    version = 8
                )
            )
        } ?: call.respond(HttpStatusCode.NotFound, Any())
    }
}
