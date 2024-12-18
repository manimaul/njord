package io.madrona.njord

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import io.madrona.njord.db.DbMigrations
import io.madrona.njord.endpoints.*
import io.madrona.njord.ext.addHandlers
import org.slf4j.event.Level
import java.io.File
import java.time.Duration


class ChartServerApp {
    fun serve() {
        embeddedServer(
            Netty,
            port = Singletons.config.port,
            host = Singletons.config.host,
            module = Application::njord
        ).start(wait = true)
    }
}

fun main() {
    ChartServerApp().serve()
}

fun Application.njord() {
    install(Compression)
    install(ContentNegotiation) {
        json()
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
    install(CORS) {
        allowHost("localhost:8080")
    }
    addHandlers(
        // curl https://openenc.com/v1/about/version | jq
        // curl https://openenc.com/v1/about/s57objects | jq
        // curl https://openenc.com/v1/about/s57attributes | jq
        AboutHandler(),

        // curl https://openenc.com/v1/tile_json | jq
        TileJsonHandler(),

        // curl https://openenc.com/v1/style/meters | jq
        StyleHandler(),

        // sig=$(curl -u ${OPEN_ENC_USER}:${OPEN_ENC_PASS} http://localhost:9000/v1/admin | jq -r .signatureEncoded)
        // curl -v "http://localhost:9000/v1/enc_save?signature=$sig" | jq
        // curl -v -X DELETE "http://localhost:9000/v1/enc_save?signature=$sig&uuid="
        // curl -v --form file="@${HOME}/Charts/ENC_ROOT.zip" 'http://localhost:8080/v1/enc_save'
        EncSaveHandler(),

        EncSaveUrlHandler(),

        ChartWebSocketHandler(),

        //curl -v -H "Content-Type: application/json" --request POST  --data '{"name": "foo", "scale": 0, "file_name": "foo.000", "updated": "1979", "issued": "1980", "zoom": 1, "dsid_props": {}, "chart_txt": {}}' https://openenc.com/v1/chart
        //curl -v -X DELETE 'https://openenc.com/v1/chart?id=1'
        //curl -v 'https://openenc.com/v1/chart?id=1' | jq
        ChartHandler(),

        //curl -v 'https://openenc.com/v1/chart_catalog' | jq
        ChartCatalogHandler(),

        //curl -v -H "Content-Type: application/json" --request POST --data-binary "@data/BOYSPP.json" 'https://openenc.com/v1/geojson?chart_id=8&name=BOYSPP'
        //curl -v -H "Content-Type: application/json" --request POST --data-binary "@${HOME}/source/madrona/njord/data/US3WA46M/ogr_BOYSPP.json" 'https://openenc.com/v1/geojson?chart_id=17&name=BOYSPP'
        //curl -v 'https://openenc.com/v1/geojson?chart_id=17&layer_name=BOYSPP' | jq
        GeoJsonHandler(),

        // curl -v "https://openenc.com/v1/tile/0/0/0"
        TileHandler(),

        // curl -v "https://openenc.com/v1/cache"
        CacheHandler(),

        // https://openenc.com/v1/icon/<name>.png
        IconHandler(),

        // curl -v "https://openenc.com/v1/content/fonts/Roboto Bold/0-255.pbf"
        // curl https://openenc.com/v1/content/sprites/day_sprites.json | jq
        // curl https://openenc.com/v1/content/sprites/day_sprites.png
        // https://openenc.com/v1/content/upload.html
        StaticResourceContentHandler(),

        // curl https://openenc.com/v1/feature/lnam/02260F22BF31214F | jq
        // curl 'https://openenc.com/v1/feature/layer/LNDARE?start_id=0' | jq
        FeatureHandler(),

        // curl -u ${OPEN_ENC_USER}:${OPEN_ENC_PASS} https://openenc.com/v1/admin
        AdminHandler(),

        //curl -u ${OPEN_ENC_USER}:${OPEN_ENC_PASS} -v -H "Content-Type: application/json" --request POST  --data "$(curl -u ${OPEN_ENC_USER}:${OPEN_ENC_PASS} https://openenc.com/v1/admin)" https://openenc.com/v1/admin/verify
        AdminVerifyHandler(),
    )

    install(StatusPages) {

        // https://openenc.com
        unhandled { call ->
            call.respondText(File(Singletons.config.webStaticContent, "index.html").readText(), ContentType.Text.Html)
        }
    }
    DbMigrations.checkVersion()
}
