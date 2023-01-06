package io.madrona.njord

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import io.madrona.njord.endpoints.*
import io.madrona.njord.ext.addHandlers
import org.slf4j.event.Level
import java.io.File
import java.time.Duration


class ChartServerApp {
    fun serve() {
        embeddedServer(Netty, port = Singletons.config.port, host = Singletons.config.host) {
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
            install(CallLogging) {
                level = Level.INFO
            }
            addHandlers(
                // curl http://localhost:9000/v1/about/version | jq
                // curl http://localhost:9000/v1/about/s57objects | jq
                // curl http://localhost:9000/v1/about/s57attributes | jq
                AboutHandler(),

                // curl http://localhost:9000/v1/tile_json | jq
                TileJsonHandler(),

                // curl http://localhost:9000/v1/style/meters | jq
                StyleHandler(),

                // curl -v --form file="@${HOME}/Charts/ENC_ROOT.zip" 'http://localhost:8080/v1/enc_save'
                EncSaveHandler(),

                ChartWebSocketHandler(),

                //curl -v -H "Content-Type: application/json" --request POST  --data '{"name": "foo", "scale": 0, "file_name": "foo.000", "updated": "1979", "issued": "1980", "zoom": 1, "dsid_props": {}, "chart_txt": {}}' http://localhost:9000/v1/chart
                //curl -v -X DELETE 'http://localhost:9000/v1/chart?id=1'
                //curl -v 'http://localhost:9000/v1/chart?id=1' | jq
                ChartHandler(),

                //curl -v 'http://localhost:9000/v1/chart_catalog' | jq
                ChartCatalogHandler(),

                //curl -v -H "Content-Type: application/json" --request POST --data-binary "@data/BOYSPP.json" 'http://localhost:9000/v1/geojson?chart_id=8&name=BOYSPP'
                //curl -v -H "Content-Type: application/json" --request POST --data-binary "@${HOME}/source/madrona/njord/data/US3WA46M/ogr_BOYSPP.json" 'http://localhost:9000/v1/geojson?chart_id=17&name=BOYSPP'
                //curl -v 'http://localhost:9000/v1/geojson?chart_id=17&layer_name=BOYSPP' | jq
                GeoJsonHandler(),

                // curl -v "http://localhost:9000/v1/tile/0/0/0"
                TileHandler(),

                // http://localhost:9000/v1/icon/<name>.png
                IconHandler(),

                // curl -v "http://localhost:9000/v1/content/fonts/Roboto Bold/0-255.pbf"
                // curl http://localhost:9000/v1/content/sprites/day_sprites.json | jq
                // curl http://localhost:9000/v1/content/sprites/day_sprites.png
                // http://localhost:9000/v1/content/upload.html
                StaticResourceContentHandler(),
            )

            install(StatusPages) {

                // http://localhost:9000
                unhandled { call ->
                    call.respondText(File(Singletons.config.webStaticContent, "index.html").readText(), ContentType.Text.Html)
                }
            }
        }.start(wait = true)
    }
}

fun main() {
    ChartServerApp().serve()
}
