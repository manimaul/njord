package io.madrona.njord.endpoints

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.EncUpload
import io.madrona.njord.util.File
import io.madrona.njord.util.UUID
import io.madrona.njord.util.logger

val client = HttpClient(Curl)

class EncSaveUrlHandler(
    config: ChartsConfig = Singletons.config,
) : KtorHandler {

    override val route = "/v1/enc_save_url"
    private val log = logger()
    private val charDir = config.chartTempData

    override suspend fun handlePost(call: ApplicationCall) = call.requireSignature {
        val urlStr = call.receive<String>().removeSurrounding("\"")
        val url = Url(urlStr)
        log.info("request to save url $url")
        url.segments.lastOrNull()?.takeIf { it.endsWith(".zip", true) }?.let { zipFileName ->
            val uuid = UUID.randomUUID().toString()
            val tempDir = File(charDir, uuid)
            val files = mutableListOf<String>()
            if (tempDir.mkdirs()) {
                log.info("fetching $url")
                val response = client.get(url = url)
                log.info("fetching $url status ${response.status}")
                if (response.status == HttpStatusCode.OK) {
                    File(tempDir, zipFileName).writeBytes(response.body())
                    files.add(zipFileName)
                    EncUpload(
                        files = files,
                        uuid = uuid
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }?.let {
            call.respond(it)
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
}