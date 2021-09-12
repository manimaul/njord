package io.madrona.njord.endpoints

import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorWebsocket
import io.madrona.njord.ext.letTwo
import io.madrona.njord.logger
import io.madrona.njord.model.EncUpload
import kotlinx.coroutines.*
import java.io.File
import java.util.zip.ZipFile

class ChartWebSocketHandler(
    config: ChartsConfig = Singletons.config,
    private val scope: CoroutineScope = Singletons.ioScope
) : KtorWebsocket {
    private val log = logger()
    private val charDir = config.chartTempData
    override val route = "/v1/ws/enc_process"

    override suspend fun handle(ws: DefaultWebSocketServerSession) {
        log.info("ws uri = ${ws.call.url()}")
        log.info("ws query keys = ${ws.call.request.queryParameters.names()}")

        letTwo(
            ws.call.request.queryParameters["uuid"],
            ws.call.request.queryParameters.getAll("file")) { uuid, files ->
            scope.launch {
                ws.processFiles(EncUpload(files, uuid))
            }
        } ?: run {
            ws.send("invalid query params")
            ws.close(CloseReason(CloseReason.Codes.NORMAL, "uuid and or files not provided"))
        }

        for (frame in ws.incoming) {
            when (frame) {
                is Frame.Text -> {
                    log.info("ws received ${frame.readText()}")
                }
                else -> {}
            }
        }
    }

    private suspend fun DefaultWebSocketServerSession.processFiles(encUpload: EncUpload) {
        unzipFiles(encUpload)
        // todo: process with gdal
        close()
    }

    private suspend fun DefaultWebSocketServerSession.unzipFiles(encUpload: EncUpload) {
        letTwo(encUpload.uuidDir(), encUpload.cacheFiles()) { dir, files ->
            files.forEach { zipFile ->
                ZipFile(zipFile).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        zip.getInputStream(entry).use { input ->
                            if (!entry.name.startsWith("__MACOSX")) {
                                send("file: ${entry.name}")
                                val outFile = File(dir, entry.name).apply {
                                    log.info("file = $absolutePath")
                                }
                                outFile.parentFile?.let {
                                    if (!it.exists()) {
                                        it.mkdirs()
                                    }
                                }
                                if (!entry.isDirectory) {
                                    outFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun EncUpload.cacheFiles()  : List<File> {
        return uuidDir()?.let { dir ->
            files.mapNotNull { name ->
                File(dir, name).takeIf {
                    it.exists()
                } ?: run {
                    log.error("chart dir file does not exist $dir/$name")
                    null
                }
            }
        } ?: emptyList()
    }

    private fun EncUpload.uuidDir()  : File? {
        return File(charDir, uuid).takeIf {
            it.exists()
        } ?: run {
            log.error("chart dir does not exist $charDir/$uuid")
            null
        }
    }
}