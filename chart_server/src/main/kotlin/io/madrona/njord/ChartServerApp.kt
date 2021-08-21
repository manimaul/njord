package io.madrona.njord

import com.willkamp.vial.api.VialServer
import io.madrona.njord.model.About
import org.gdal.gdal.gdal


class ChartServerApp {

    fun serve() {
        gdal.AllRegister()
        gdal.SetConfigOption("OGR_S57_OPTIONS", "LNAM_REFS:ON,UPDATES:ON,SPLIT_MULTIPOINT:ON,PRESERVE_EMPTY_NUMBERS:ON,RETURN_LINKAGES:ON")
        VialServer.create()
                ///curl http://localhost:9000/v1/about | jq
                .httpGet("/v1/about") { request ->
                    request.respondWith {
                        it.setBodyJson(
                                About(
                                        version = "1.0",
                                        gdalVersion = gdal.VersionInfo() ?: "NONE"
                                )
                        )
                    }
                }.listenAndServeBlocking()
    }
}

fun main() {
    ChartServerApp().serve()
}