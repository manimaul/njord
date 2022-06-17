package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.fromString
import io.madrona.njord.layers.*
import io.madrona.njord.model.*

class StyleHandler(
    private val config: ChartsConfig = Singletons.config,
    private val layerFactory: LayerFactory = LayerFactory()
) : KtorHandler {
    override val route = "/v1/style/{depth}"

    override suspend fun handleGet(call: ApplicationCall) {
        fromString<Depth>(call.parameters["depth"])?.let { depth ->
            call.respond(
                Style(
                    name = config.chartSymbolSprites,
                    glyphsUrl = "${config.externalBaseUrl}/v1/content/fonts/{fontstack}/{range}.pbf",
                    spriteUrl = "${config.externalBaseUrl}/v1/content/sprites/${config.chartSymbolSprites}",
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
