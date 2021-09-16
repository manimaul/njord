package io.madrona.njord

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import io.madrona.njord.endpoints.*
import io.madrona.njord.ext.addHandlers
import java.time.Duration


class ChartServerApp {
    fun serve() {
        embeddedServer(Netty, port = 9000, host = "0.0.0.0") {
            install(ContentNegotiation) {
                jackson {
                    Singletons.objectMapper
                }
            }
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(15)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            addHandlers(
                // curl http://localhost:9000/v1/about | jq
                AboutHandler(),

                // curl http://localhost:9000/v1/tile_json | jq
                TileJsonHandler(),

                // curl http://localhost:9000/v1/style/day/meters | jq
                StyleHandler(),

                // curl -v "http://localhost:9000/v1/content/fonts/Roboto Bold/0-255.pbf"
                // curl http://localhost:9000/v1/content/sprites/rastersymbols-day.json | jq
                // curl http://localhost:9000/v1/content/sprites/rastersymbols-day.png
                // http://localhost:9000/v1/content/upload.html
                StaticContentHandler(),

                // curl -v --form file="@${HOME}/Charts/ENC_ROOT.zip" 'http://localhost:8080/v1/enc_save'
                EncSaveHandler(),

                ChartWebSocketHandler(),
            )
        }.start(wait = true)
    }
}

fun main() {
    ChartServerApp().serve()
}
