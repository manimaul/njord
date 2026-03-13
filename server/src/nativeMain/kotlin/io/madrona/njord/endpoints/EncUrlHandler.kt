@file:OptIn(ExperimentalForeignApi::class)

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
import io.ktor.utils.io.readAvailable
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.EncUrlRequest
import io.madrona.njord.util.logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

private const val CHUNK_SIZE = 8 * 1024 * 1024 // 8 MB

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
            val tmp = File(chartDir, "$fileName.tmp")
            runCatching {
                log.info("downloading $url -> $fileName")
                val dest = File(chartDir, fileName)
                val tmpPath = tmp.getAbsolutePath().toString()
                val fp = fopen(tmpPath, "wb")
                    ?: throw IllegalStateException("cannot open $tmpPath for writing")
                var totalBytes = 0L
                try {
                    client.prepareGet(url).execute { response ->
                        val contentLength = response.contentLength()
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(CHUNK_SIZE)
                        var lastLoggedPct = -1
                        while (!channel.isClosedForRead) {
                            val count = channel.readAvailable(buffer)
                            if (count > 0) {
                                buffer.usePinned { pinned ->
                                    fwrite(pinned.addressOf(0), 1u, count.toULong(), fp)
                                }
                                totalBytes += count
                                if (contentLength != null && contentLength > 0) {
                                    val pct = (totalBytes * 100L / contentLength).toInt() / 10 * 10
                                    if (pct > lastLoggedPct) {
                                        log.info("downloading $fileName: $pct%")
                                        lastLoggedPct = pct
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    fclose(fp)
                }
                tmp.renameTo(dest.getAbsolutePath().toString())
                log.info("download complete: $fileName ($totalBytes bytes)")
            }.onFailure {
                log.error("failed to download $url: ${it.message}")
                if (tmp.exists()) {
                    tmp.deleteRecursively()
                }
            }
        }

        call.respond(HttpStatusCode.Accepted, EncUpload(zipFile = fileName))
    }
}
