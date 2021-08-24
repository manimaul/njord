package io.madrona.njord.endpoints

import com.willkamp.vial.api.EndPointHandler
import com.willkamp.vial.api.Request
import io.madrona.njord.ChartsConfig
import io.madrona.njord.ext.letFromStrings
import io.madrona.njord.layers.Background
import io.madrona.njord.layers.Depare
import io.madrona.njord.layers.LayerFactory
import io.madrona.njord.layers.Seaare
import io.madrona.njord.model.*
import io.netty.handler.codec.http.HttpResponseStatus

private const val color = "color"
private const val depth = "depth"

class StyleHandler(
        private val config: ChartsConfig,
        private val layerFactory: LayerFactory = LayerFactory()
) : EndPointHandler {
    override val route = "/v1/style/:$color/:$depth"

    override fun handle(request: Request) {
        letFromStrings(request.pathParam(color), request.pathParam(depth)) { color: StyleColor, depth: Depth ->
            val name = "${color.name.toLowerCase()}-${depth.name.toLowerCase()}"
            request.respondWith {
                it.setBodyJson(
                    Style(
                        name = name,
                        glyphsUrl = "${config.externalBaseUrl}/v1/font/{fontstack}/{range}.pbf",
                        spriteUrl = "${config.externalBaseUrl}/sprites/rastersymbols-${color.name.toLowerCase()}",
                        sources = mapOf(
                            "src_senc" to Source(
                                type = SourceType.VECTOR,
                                tileJsonUrl = "${config.externalBaseUrl}/v1/tile_json"
                            )
                        ),

                        layers = layerFactory.layers(color),
                        version = 8
                    )
                )
            }
        } ?: request.respondWith {
            it.setStatus(HttpResponseStatus.NOT_FOUND).setBodyText("not found")
        }
    }


}