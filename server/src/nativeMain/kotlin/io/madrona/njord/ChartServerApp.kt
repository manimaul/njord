package io.madrona.njord

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.madrona.njord.db.DbMigrations
import io.madrona.njord.endpoints.*
import io.madrona.njord.ext.addHandler
import io.madrona.njord.util.File
import kotlin.time.Duration.Companion.seconds

val CallLogging = createApplicationPlugin(name = "CallLogging") {
    onCall { call ->
        val origin = call.request.origin
        println("${origin.method} ${origin.uri} ${origin.remoteHost}")
    }
}

class ChartServerApp {
    fun serve() {
        embeddedServer(
            CIO,
            port = Singletons.config.port,
            host = Singletons.config.host,
            module = Application::njord
        ).start(wait = true)
    }
}

fun Application.njord() {
//    install(Compression)
    install(ContentNegotiation) {
        json()
    }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(CallLogging)
    Singletons.genLog = log
    install(CORS) {
        allowHost("*")
//        Singletons.config.allowHosts.forEach {
//            allowHost(it)
//        }
        allowHeader(HttpHeaders.ContentType)
    }
    install(Authentication) {
        basic("auth-basic") {
            realm = "Admin"
            validate { credentials ->
                if (Singletons.config.adminUser == credentials.name && Singletons.config.adminPass == credentials.password) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }

    routing {
        authenticate("auth-basic") {
            addHandler(
                // curl -u ${OPEN_ENC_USER}:${OPEN_ENC_PASS} https://openenc.com/v1/admin
                AdminHandler()
            )
        }

        listOf(
            // curl https://openenc.com/v1/about/version | jq
            // curl https://openenc.com/v1/about/s57objects | jq
            // curl https://openenc.com/v1/about/s57attributes | jq
            AboutHandler(),

            // curl https://openenc.com/v1/tile_json | jq
            TileJsonHandler(),

            // curl https://openenc.com/v1/style/meters/day | jq
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


            //curl -u ${OPEN_ENC_USER}:${OPEN_ENC_PASS} -v -H "Content-Type: application/json" --request POST  --data "$(curl -u ${OPEN_ENC_USER}:${OPEN_ENC_PASS} https://openenc.com/v1/admin)" https://openenc.com/v1/verify_admin
            AdminVerifyHandler(),
        ).forEach {
            addHandler(it)
        }
    }

    install(StatusPages) {

        // https://openenc.com
        unhandled { call ->
            val name = call.request.uri.substringAfterLast('/')
            File(Singletons.config.webStaticContent, name).takeIf { it.exists() && it.isFile() }?.let {
                call.respondText(it.readContents(), ContentType.fromFilePath(name).firstOrNull())
            } ?: run{
                call.respondText(File(Singletons.config.webStaticContent, "index.html").readContents(), ContentType.Text.Html)
            }
        }
    }
    DbMigrations.checkVersion()
}
