package io.madrona.njord.endpoints

import io.ktor.server.request.*
import io.ktor.server.util.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.GeoJsonDao
import io.madrona.njord.db.InsertError
import io.madrona.njord.db.InsertSuccess
import io.madrona.njord.ext.KtorWebsocket
import io.madrona.njord.ext.letTwo
import io.madrona.njord.geo.S57
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.FeatureInsert
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.model.ws.sendMessage
import io.madrona.njord.util.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile

class ChartWebSocketHandler(
    config: ChartsConfig = Singletons.config,
    private val chartDao: ChartDao = ChartDao(),
    private val geoJsonDao: GeoJsonDao = GeoJsonDao(),
    private val scope: CoroutineScope = Singletons.ioScope
) : KtorWebsocket {
    private val log = logger()
    private val charDir = config.chartTempData
    override val route = "/v1/ws/enc_process"

    override suspend fun handle(ws: DefaultWebSocketServerSession) = ws.call.requireSignature {
        log.info("ws uri = ${ws.call.url()}")
        log.info("ws query keys = ${ws.call.request.queryParameters.names()}")

        letTwo(
            ws.call.request.queryParameters["uuid"],
            ws.call.request.queryParameters.getAll("file")
        ) { uuid, files ->
            scope.launch {
                log.info("processing files for uuid = $uuid")
                ws.processFiles(EncUpload(files, uuid))
            }
        } ?: run {
            log.error("ws invalid query params ${ws.call.request.queryString()}")
            ws.sendMessage(
                WsMsg.FatalError(
                    message = "invalid query params",
                )
            )
            ws.close(CloseReason(CloseReason.Codes.NORMAL, "uuid and or files not provided"))
        }

        for (frame in ws.incoming) {
            when (frame) {
                is Frame.Text -> {
                    log.info("ws received ${frame.readText()}")
                }
                else -> {
                    log.error("ws received unknown frame")
                }
            }
        }
    }

    /**
     * Process zipped, uploaded s57 chart files. See [EncSaveHandler]
     *
     * 1. Unzip [EncUpload.cacheFiles]
     * 2. Read .000 S57 files and convert them to geojson files by layer
     * 3. Read DSID layer and create chart (require WS confirmation for upsert) todo:
     * 4. Insert geojson layers into database todo:
     */
    private suspend fun DefaultWebSocketServerSession.processFiles(encUpload: EncUpload) {
        encUpload.uuidDir()?.let { dir ->
            unzipFiles(encUpload).filter {
                it.name.endsWith(".000")
            }.map {
                sendMessage(
                    WsMsg.InsertionStatus(
                        message = "reading file",
                        chartName = it.name,
                        isError = false
                    )
                )
                S57(it)
            }.forEach { s57 ->
                when (val insert = s57.chartInsertInfo()) {
                    is InsertError -> {
                        scope.launch {
                            sendMessage(
                                WsMsg.InsertionStatus(
                                    message = insert.msg,
                                    chartName = s57.file.name,
                                    isError = true
                                )
                            )
                        }
                        null
                    }

                    is InsertSuccess -> chartDao.insertAsync(insert.value).await()
                }?.let { chart ->
                    scope.launch {
                        sendMessage(
                            WsMsg.InsertionStatus(
                                chartName = chart.name,
                                message = "created chart id=${chart.id}"
                            )
                        )
                    }
                    val layersToInsert = s57.layerGeoJsonSequence(exLayers)
                    layersToInsert.forEach { (name, fc) ->
                        val count = geoJsonDao.insertAsync(
                            FeatureInsert(
                                layerName = name,
                                chart = chart,
                                geo = fc
                            )
                        ).await()
                        scope.launch {
                            sendMessage(
                                WsMsg.Insertion(
                                    chartName = chart.name,
                                    featureCount = count ?: 0,
                                )
                            )
                        }
                    }
                }
            }
            dir.deleteRecursively()
        }
        close()
    }

    private suspend fun DefaultWebSocketServerSession.unzipFiles(encUpload: EncUpload): List<File> {
        val retVal = mutableListOf<File>()
        letTwo(encUpload.uuidDir(), encUpload.cacheFiles()) { dir, files ->
            files.forEach { zipFile ->
                ZipFile(zipFile).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        zip.getInputStream(entry).use { input ->
                            if (!entry.name.startsWith("__MACOSX")) {
                                sendMessage(
                                    WsMsg.Info("extracting file: ${entry.name}")
                                )
                                val outFile = File(dir, entry.name)
                                outFile.parentFile?.let {
                                    if (!it.exists()) {
                                        it.mkdirs()
                                    }
                                }
                                if (!entry.isDirectory) {
                                    outFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                    retVal.add(outFile)
                                }
                            }
                        }
                    }
                }
            }
        }
        return retVal
    }

    private fun EncUpload.cacheFiles(): List<File> {
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

    private fun EncUpload.uuidDir(): File? {
        return File(charDir, uuid).takeIf {
            it.exists()
        } ?: run {
            log.error("chart dir does not exist $charDir/$uuid")
            null
        }
    }

    companion object {
        val exLayers = setOf("DSID", "IsolatedNode", "ConnectedNode", "Edge", "Face")
    }
}