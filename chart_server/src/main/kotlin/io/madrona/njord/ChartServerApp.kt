package io.madrona.njord

import com.willkamp.vial.api.ResponseBuilder
import com.willkamp.vial.api.VialServer
import java.lang.IllegalStateException


class ChartServerApp {

    fun serve() {
        val token = System.getenv("MAPBOX_TOKEN")
        if (token.isNullOrBlank()) {
            throw IllegalStateException("empty token")
        }
        val indexHtml = resourceString("index.html")!!.replace("{TOKEN}", token)
        VialServer.create().httpGet("/") { _, responseBuilder: ResponseBuilder ->
            responseBuilder.setBodyHtml(indexHtml)
        }.listenAndServeBlocking()
    }
}

fun main() {
    ChartServerApp().serve()
}