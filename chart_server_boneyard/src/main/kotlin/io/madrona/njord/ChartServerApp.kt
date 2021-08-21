package io.madrona.njord

import com.willkamp.vial.api.VialServer
import org.gdal.gdal.gdal


class ChartServerApp {

    fun serve() {
        VialServer.create().httpGet("/gdalversion") { request, responseBuilder ->
            gdal.AllRegister()
            responseBuilder.setBodyText(gdal.VersionInfo())
        }.listenAndServeBlocking()
//        val token = System.getenv("MAPBOX_TOKEN")
//        if (token.isNullOrBlank()) {
//            throw IllegalStateException("empty token")
//        }
//        val indexHtml = resourceString("index.html")!!.replace("{TOKEN}", token)
//        VialServer.create().httpGet("/") { _, responseBuilder: ResponseBuilder ->
//            responseBuilder.setBodyHtml(indexHtml)
//        }.listenAndServeBlocking()
    }
}

fun main() {
    ChartServerApp().serve()
}