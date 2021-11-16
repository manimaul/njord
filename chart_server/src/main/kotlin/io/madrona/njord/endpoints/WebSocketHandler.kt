package io.madrona.njord.endpoints

import io.ktor.http.cio.websocket.*
import io.ktor.util.*
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
import io.madrona.njord.util.logger
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.FeatureInsert
import kotlinx.coroutines.*
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

    override suspend fun handle(ws: DefaultWebSocketServerSession) {
        log.info("ws uri = ${ws.call.url()}")
        log.info("ws query keys = ${ws.call.request.queryParameters.names()}")

        letTwo(
            ws.call.request.queryParameters["uuid"],
            ws.call.request.queryParameters.getAll("file")
        ) { uuid, files ->
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
                else -> {
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
                send("reading ${it.name}")
                S57(it)
            }.forEach { s57 ->
                when (val insert = s57.chartInsertInfo()) {
                    is InsertError -> {
                        scope.launch {
                            send("error creating chart ${s57.file.name} msg = ${insert.msg}")
                        }
                        null
                    }
                    is InsertSuccess -> chartDao.insertAsync(insert.value).await()
                }?.let { chart ->
                    scope.launch {
                        send("created chart id=${chart.id}")
                    }
                    s57.layerGeoJsonSequence(exLayers).forEach { (name, fc) ->
                        val count = geoJsonDao.insertAsync(
                            FeatureInsert(
                                layerName = name,
                                chart = chart,
                                geo = fc
                            )
                        ).await()
                        scope.launch {
                            send("created $count records for layer $name")
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
                                send("extracting file: ${entry.name}")
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