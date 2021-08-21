package io.madrona.njord

import com.willkamp.vial.api.VialServer
import org.gdal.gdal.gdal


class ChartServerApp {

    fun serve() {
        VialServer.create().httpGet("/gdalversion") { request, responseBuilder ->
            gdal.AllRegister()
            responseBuilder.setBodyText(gdal.VersionInfo())
        }.listenAndServeBlocking()
    }
}

fun main() {
    ChartServerApp().serve()
}