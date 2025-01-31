package io.madrona.njord.ingest

import io.ktor.websocket.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.*
import io.madrona.njord.ext.letTwo
import io.madrona.njord.geo.S57
import io.madrona.njord.model.Chart
import io.madrona.njord.model.ChartInsert
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.FeatureInsert
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.model.ws.sendMessage
import io.madrona.njord.util.logger
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile

class ChartIngest(
    private val webSocketSession: WebSocketSession,
    config: ChartsConfig = Singletons.config,
    private val chartDao: ChartDao = ChartDao(),
    private val geoJsonDao: GeoJsonDao = GeoJsonDao(),
    private val tileDao: TileDao = Singletons.tileDao,
) {

    private val charDir = config.chartTempData
    private val log = logger()

    suspend fun ingest(encUpload: EncUpload) {

        val s57Files = withContext(Dispatchers.IO) {
            step1UnzipFiles(encUpload)
        }
        val report = Report(
            totalFeatureCount = s57Files.sumOf { it.featureCount(exLayers) },
            totalChartCount = s57Files.size.toLong()
        )
        webSocketSession.sendMessage(report.progressMessage())
        val inserts = withContext(Dispatchers.IO) {
            step2InsertData(s57Files, report).filterNotNull()
        }
        step3(inserts, report)
        webSocketSession.sendMessage(report.completionMessage())

        withContext(Dispatchers.IO) {
            encUpload.uuidDir()?.deleteRecursively()
        }
        log.info("clearing tile cache")
        tileDao.logStats()
        tileDao.clearCache()
        tileDao.logStats()
    }

    private suspend fun step1UnzipFiles(
        encUpload: EncUpload,
    ): List<S57> {
        webSocketSession.sendMessage(
            WsMsg.Extracting(1, 0f)
        )
        val retVal = mutableListOf<File>()
        return letTwo(encUpload.uuidDir(), encUpload.cacheFiles()) { dir, files ->
            files.forEach { zipFile ->
                ZipFile(zipFile).use { zip ->
                    val size = zip.size().toFloat()
                    var complete = 0f
                    zip.entries().asSequence().forEach { entry ->
                        zip.getInputStream(entry).use { input ->
                            if (!entry.name.startsWith("__MACOSX")) {
                                val outFile = File(dir, entry.name)
                                outFile.parentFile?.let {
                                    if (!it.exists()) {
                                        it.mkdirs()
                                    }
                                }
                                if (!entry.isDirectory) {
                                    outFile.outputStream().use { output ->
                                        input.copyTo(output)
                                        complete += 1
                                        webSocketSession.sendMessage(
                                            WsMsg.Extracting(1, complete / size)
                                        )
                                    }
                                    retVal.add(outFile)
                                }
                            }
                        }
                    }
                }
            }
            retVal.mapNotNull {
                S57.from(it)
            }.also {
                webSocketSession.sendMessage(
                    WsMsg.Extracting(1, 1f)
                )
            }
        } ?: emptyList()
    }

    private suspend fun step2InsertData(
        s57Files: List<S57>, report: Report
    ): List<Pair<S57, InsertSuccess<ChartInsert>>?> {
        webSocketSession.sendMessage(WsMsg.Extracting(2, 0f))
        return s57Files.map { s57 ->
            var complete = 0f
            val all = s57Files.size.toFloat()
            when (val insert = s57.chartInsertInfo()) {
                is InsertError -> {
                    report.failChart(s57.file.name, insert.msg)
                    null
                }

                is InsertSuccess -> {
                    s57 to insert
                }
            }.also {
                complete += 1
                webSocketSession.sendMessage(WsMsg.Extracting(2, complete / all))
            }
        }
    }

    private suspend fun step3(
        data: List<Pair<S57, InsertSuccess<ChartInsert>>>,
        report: Report
    ) {
        val queue = ConcurrentLinkedDeque(data)
        val working = AtomicInteger(0)

        withContext(Dispatchers.IO) {
            while (queue.isNotEmpty()) {
                if (working.get() >= 100) {
                    delay(500)
                } else {
                    val (s57, insert) = queue.poll()
                    log.info("installing chart ${s57.file} ${working.incrementAndGet()}")
                    launch {
                        chartDao.insertAsync(insert.value, true)?.let { chart ->
                            installChart(chart, s57, report)
                        } ?: run {
                            val msg = "${s57.file.name} (step3)"
                            log.warn(msg)
                            report.failChart(s57.file.name, msg)
                        }
                        log.info("completed chart ${s57.file} ${working.decrementAndGet()}")
                    }
                }
            }
        }
        log.info("step 3 completed")
    }

    private suspend fun countFeatures(s57: S57): Int {
        s57.layerNames()
        return 0
    }

    private suspend fun installChart(chart: Chart, s57: S57, report: Report) {
        withContext(Dispatchers.IO) {
            s57.layerGeoJsonSequence(exLayers).map { (layerName, geo) ->
                async {
                    val count = geoJsonDao.insertAsync(
                        FeatureInsert(
                            layerName = layerName, chart = chart, geo = geo
                        )
                    ) ?: 0
                    if (count == 0) {
                        log.debug("error inserting feature with layer = $layerName geo feature count = ${geo.features.size}")
                        log.debug("geo json = \n${geo}")
                    }
                    report.appendChartFeatureCount(chart.name, count)
                    webSocketSession.sendMessage(report.progressMessage())
                }
            }.toList().awaitAll()
            report.completedChart()
            webSocketSession.sendMessage(report.progressMessage())
        }
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

private data class Report(
    val totalFeatureCount: Long,
    val totalChartCount: Long,
) {

    private val featureInstallCount = AtomicLong(0)
    private val chartInstallCount = AtomicInteger(0)
    private val insertItems: MutableMap<String, Int> = Collections.synchronizedMap(mutableMapOf())
    private val failedCharts: MutableMap<String, String> = Collections.synchronizedMap(mutableMapOf())
    private val time = System.currentTimeMillis()
    private fun elapsed(): Long {
        return System.currentTimeMillis() - time
    }

    fun progressMessage() = WsMsg.Info(
        feature = featureInstallCount.get(),
        chart = chartInstallCount.get(),
        totalCharts = totalChartCount,
        totalFeatures = totalFeatureCount,
    )

    fun completionMessage() = WsMsg.CompletionReport(
        totalFeatureCount = totalFeatureCount,
        totalChartCount = totalChartCount,
        failedCharts = failedCharts.map { "${it.key} msg: ${it.value}" },
        items = insertItems.map { WsMsg.InsertItem(it.key, it.value) },
        ms = elapsed()
    )

    fun appendChartFeatureCount(chartName: String, featureCount: Int) {
        featureInstallCount.getAndUpdate { featureCount.toLong() }
        insertItems[chartName] = (insertItems[chartName] ?: 0) + featureCount
    }

    fun failChart(name: String, msg: String) {
        failedCharts.compute(name) { _, value -> value?.let { "$it, $msg" } ?: msg}
    }

    fun completedChart() {
        chartInstallCount.incrementAndGet()
    }
}
