package io.madrona.njord

import com.willkamp.vial.api.VialServer
import io.madrona.njord.endpoints.AboutHandler
import io.madrona.njord.endpoints.FontHandler
import io.madrona.njord.endpoints.StyleHandler
import io.madrona.njord.endpoints.TileJsonHandler
import org.gdal.gdal.gdal


class ChartServerApp {
    fun serve() {
        gdal.AllRegister()
        gdal.SetConfigOption("OGR_S57_OPTIONS", "LNAM_REFS:ON,UPDATES:ON,SPLIT_MULTIPOINT:ON,PRESERVE_EMPTY_NUMBERS:ON,RETURN_LINKAGES:ON")

        VialServer.create().apply {
            // curl -k https://localhost:9000/v1/about | jq
            addHandler(AboutHandler())

            // curl -k https://localhost:9000/v1/tile_json | jq
            addHandler(TileJsonHandler(vialConfig))

            // curl -k https://localhost:9000/v1/style/day/meters | jq
            addHandler(StyleHandler())

            // curl -kv "https://localhost:9000/v1/fonts/Roboto Bold/0-255.pbf"
            addHandler(FontHandler())
        }.listenAndServeBlocking()
    }
}

fun main() {
    ChartServerApp().serve()
}