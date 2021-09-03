package io.madrona.njord

import com.willkamp.vial.api.VialServer
import io.madrona.njord.endpoints.*
import org.gdal.gdal.gdal


class ChartServerApp {
    fun serve() {
        gdal.AllRegister()
        gdal.SetConfigOption("OGR_S57_OPTIONS", "LNAM_REFS:ON,UPDATES:ON,SPLIT_MULTIPOINT:ON,PRESERVE_EMPTY_NUMBERS:ON,RETURN_LINKAGES:ON")

        val config = ChartsConfig()
        VialServer.create().apply {
            // curl http://localhost:9000/v1/about | jq
            addHandler(AboutHandler())

            // curl http://localhost:9000/v1/tile_json | jq
            addHandler(TileJsonHandler(config))

            // curl http://localhost:9000/v1/style/day/meters | jq
            addHandler(StyleHandler(config))

            // curl -v "http://localhost:9000/v1/content/fonts/Roboto Bold/0-255.pbf"
            // curl http://localhost:9000/v1/content/sprites/rastersymbols-day.json | jq
            // curl http://localhost:9000/v1/content/sprites/rastersymbols-day.png
            // http://localhost:9000/v1/content/upload.html
            addHandler(StaticContentHandler())
        }.listenAndServeBlocking()
    }
}

fun main() {
    ChartServerApp().serve()
}