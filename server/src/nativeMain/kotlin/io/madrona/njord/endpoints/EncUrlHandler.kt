package io.madrona.njord.endpoints

import File
import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.EncUrlRequest
import io.madrona.njord.util.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class EncUrlHandler(
    val chartDir: File = Singletons.chartUploadDir,
) : KtorHandler, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    override val route = "/v1/enc_url"
    private val log = logger()
    private val client = HttpClient(Curl)

    override suspend fun handlePost(call: ApplicationCall) = call.requireSignature {
        val request = call.receive<EncUrlRequest>()
        val url = request.url
        val fileName = url.substringAfterLast('/').substringBefore('?')
            .takeIf { it.isNotBlank() && it.endsWith(".zip", ignoreCase = true) }
            ?: "chart_${Clock.System.now().toEpochMilliseconds()}.zip"

        launch {
            runCatching {
                log.info("downloading $url -> $fileName")
                val tmp = File(chartDir, "$fileName.tmp")
                val dest = File(chartDir, fileName)
                val bytes = client.get(url).readRawBytes()
                tmp.writeBytes(bytes)
                tmp.renameTo(dest.getAbsolutePath().toString())
                log.info("download complete: $fileName (${bytes.size} bytes)")
            }.onFailure {
                log.error("failed to download $url: ${it.message}")
            }
        }

        call.respond(HttpStatusCode.Accepted, EncUpload(zipFile = fileName))
    }
}
