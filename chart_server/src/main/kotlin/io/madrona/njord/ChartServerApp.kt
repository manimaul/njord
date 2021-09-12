package io.madrona.njord

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import io.madrona.njord.endpoints.*
import org.gdal.gdal.gdal
import io.madrona.njord.ext.addHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Duration

val objectMapper: JsonMapper by lazy {
    jsonMapper {
        addModule(kotlinModule())
    }
}

class ChartServerApp {
    fun serve() {
        gdal.AllRegister()
        gdal.SetConfigOption("OGR_S57_OPTIONS", "LNAM_REFS:ON,UPDATES:ON,SPLIT_MULTIPOINT:ON,PRESERVE_EMPTY_NUMBERS:ON,RETURN_LINKAGES:ON")

        val config = ChartsConfig()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        embeddedServer(Netty, port = 9000, host = "0.0.0.0") {
            install(ContentNegotiation) {
                jackson {
                    objectMapper
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
                TileJsonHandler(config),

                // curl http://localhost:9000/v1/style/day/meters | jq
                StyleHandler(config),

                // curl -v "http://localhost:9000/v1/content/fonts/Roboto Bold/0-255.pbf"
                // curl http://localhost:9000/v1/content/sprites/rastersymbols-day.json | jq
                // curl http://localhost:9000/v1/content/sprites/rastersymbols-day.png
                // http://localhost:9000/v1/content/upload.html
                StaticContentHandler(),

                // curl -v --form file="@${HOME}/Charts/ENC_ROOT.zip" 'http://localhost:8080/v1/enc_save'
                EncSaveHandler(config),

                ChartWebSocketHandler(scope),
            )
        }.start(wait = true)
    }
}

fun main() {
    ChartServerApp().serve()
}
