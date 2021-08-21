package io.madrona.njord

import com.willkamp.vial.api.VialServer
import io.madrona.njord.model.About
import io.madrona.njord.model.tileJson
import org.gdal.gdal.gdal


class ChartServerApp {

    fun serve() {
        gdal.AllRegister()
        gdal.SetConfigOption("OGR_S57_OPTIONS", "LNAM_REFS:ON,UPDATES:ON,SPLIT_MULTIPOINT:ON,PRESERVE_EMPTY_NUMBERS:ON,RETURN_LINKAGES:ON")
        val server = VialServer.create()
        server
                // curl -k https://localhost:9000/v1/about | jq
                .httpGet("/v1/about") { request ->
                    request.respondWith {
                        it.setBodyJson(
                                About(
                                        version = "1.0",
                                        gdalVersion = gdal.VersionInfo() ?: "NONE"
                                )
                        )
                    }
                }
                // curl -k https://localhost:9000/v1/tileJson | jq
                .httpGet("/v1/tileJson") { request ->
                    request.respondWith {
                        it.setBodyJson(tileJson("${"127.0.0.1"}:${server.vialConfig.port}"))
                    }
                }
                .listenAndServeBlocking()
    }
}

fun main() {
    ChartServerApp().serve()
}