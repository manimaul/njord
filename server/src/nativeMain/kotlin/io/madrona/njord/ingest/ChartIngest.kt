package io.madrona.njord.ingest

import File
import OgrVectorDataset
import io.ktor.websocket.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.*
import io.madrona.njord.model.*
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.model.ws.sendMessage
import io.madrona.njord.util.logger
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong

class ChartIngest(
    private val webSocketSession: WebSocketSession,
    private val config: ChartsConfig = Singletons.config,
    private val chartDao: ChartDao = ChartDao(),
    private val geoJsonDao: GeoJsonDao = GeoJsonDao(),
    private val tileDao: TileDao = Singletons.tileDao,
) {

    private val charDir = config.chartTempData
    private val log = logger()
    private val working = AtomicInt(0)

    suspend fun ingest(encUpload: EncUpload) {
        log.info("ingesting enc upload $encUpload")
        val s57Files = withContext(Dispatchers.IO) {
            log.info("unzipping files")
            step1UnzipFiles(encUpload)
        }.filter { it.name.endsWith(".000") }

        val featureCount = withContext(Dispatchers.IO) {
            log.info("counting features")
            s57Files.sumOf { file ->
                log.info("counting features: $file")
                OgrVectorDataset(file.toString()).featureCount(exLayers)
            }
        }
        val report = Report(
            totalFeatureCount = featureCount.toLong(),
            totalChartCount = s57Files.size
        )
        webSocketSession.sendMessage(report.progressMessage())
        log.info("inserting data")
        val queue = ArrayDeque(s57Files)
        working.getAndSet(0)
        withContext(Dispatchers.IO) {
            val chartsWorking = AtomicInt(0)
            while (queue.isNotEmpty()) {
                if (chartsWorking.value < config.chartIngestWorkers) {
                    queue.removeFirstOrNull()?.let { file ->
                        val w = chartsWorking.incrementAndGet()
                        log.info("inserting chart ${file.name} working = $w")
                        launch {
                            OgrVectorDataset(file.toString()).let{ s57 ->
                                chartInsertData(s57, report)?.let { data ->
                                    chartDao.insertAsync(data, true)?.let {
                                        installChartFeatures(s57, it, report)
                                    } ?: run {
                                        report.failChart(file.name, "chart insert")
                                    }
                                }
                            }
                            chartsWorking.decrementAndGet()
                        }
                    }
                } else {
                    delay(250)
                }
            }
        }
        webSocketSession.sendMessage(report.completionMessage())

        log.info("cleaning up resources")
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
    ): List<File> {
        TODO()
//        webSocketSession.sendMessage(
//            WsMsg.Extracting(0f)
//        )
//        val retVal = mutableListOf<File>()
//        return letTwo(encUpload.uuidDir(), encUpload.cacheFiles()) { dir, files ->
//            files.forEach { zipFile ->
//                ZipFile(zipFile).use { zip ->
//                    val size = zip.size().toFloat()
//                    var complete = 0f
//                    zip.entries().asSequence().forEach { entry ->
//                        zip.getInputStream(entry).use { input ->
//                            if (!entry.name.startsWith("__MACOSX")) {
//                                val outFile = File(dir, entry.name)
//                                outFile.parentFile?.let {
//                                    if (!it.exists()) {
//                                        it.mkdirs()
//                                    }
//                                }
//                                if (!entry.isDirectory) {
//                                    outFile.outputStream().use { output ->
//                                        input.copyTo(output)
//                                        complete += 1
//                                        webSocketSession.sendMessage(
//                                            WsMsg.Extracting(complete / size)
//                                        )
//                                    }
//                                    retVal.add(outFile)
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            retVal.also {
//                webSocketSession.sendMessage(
//                    WsMsg.Extracting(1f)
//                )
//            }
//        } ?: emptyList()
    }

    private fun chartInsertData(
        s57: OgrVectorDataset, report: Report
    ): ChartInsert? {
        TODO()
//        return when (val insert = s57.chartInsertInfo()) {
//            is InsertError -> {
//                report.failChart(s57.file.name, insert.msg)
//                null
//            }
//
//            is InsertSuccess -> {
//                insert.value
//            }
//        }
    }

    private suspend fun installChartFeatures(
        s57: OgrVectorDataset,
        chart: Chart, report: Report) {
        TODO()
//        val queue = ArrayDeque(s57.layerNames.filter { !exLayers.contains(it) })
//        withContext(Dispatchers.IO) {
//            while (queue.isNotEmpty()) {
//                if (working.get() < config.featureIngestWorkers) {
//                    queue.poll()?.let { layerName ->
//                        s57.findLayer(layerName)?.let { geo ->
//                            val w = working.incrementAndGet()
//                            val r = queue.size
//                            launch {
//                                log.info("$layerName ${geo.features.size} feature(s) inserting working=$w remaining=$r")
//                                val ctx = timer.time()
//                                val count = geoJsonDao.featureInsertAsync(
//                                    FeatureInsert(
//                                        layerName = layerName, chart = chart, geo = geo
//                                    )
//                                ) ?: 0
//                                val ms = ctx.stop() / 1000L
//                                val w = working.decrementAndGet()
//                                if (count == 0) {
//                                    log.debug("error inserting feature with layer = $layerName geo feature count = ${geo.features.size}")
//                                    log.debug("geo json = \n${geo}")
//                                }
//                                log.info("$layerName $count feature(s) inserted in $ms working=$w")
//                                report.appendChartFeatureCount(chart.name, count)
//                                webSocketSession.sendMessage(report.progressMessage())
//                            }
//                        }
//                    }
//                } else {
//                    delay(250)
//                }
//            }
//            report.completedChart()
//            webSocketSession.sendMessage(report.progressMessage())
//        }
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
    val totalChartCount: Int,
) {

    private val featureInstallCount = AtomicLong(0)
    private val chartInstallCount = AtomicInt(0)
    private val insertItems: MutableMap<String, Int> = mutableMapOf() //Collections.synchronizedMap(mutableMapOf())
    private val failedCharts: MutableMap<String, String> = mutableMapOf() //Collections.synchronizedMap(mutableMapOf())
    private val time = Clock.System.now().toEpochMilliseconds()
    private fun elapsed(): Long {
        return Clock.System.now().toEpochMilliseconds() - time
    }

    fun progressMessage() = WsMsg.Info(
        feature = featureInstallCount.value,
        chart = chartInstallCount.value,
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
        featureInstallCount.getAndAdd(featureCount.toLong())
        insertItems[chartName] = (insertItems[chartName] ?: 0) + featureCount
    }

    fun failChart(name: String, msg: String) {
//        failedCharts.compute(name) { _, value -> value?.let { "$it, $msg" } ?: msg }
        TODO()
    }

    fun completedChart() {
        chartInstallCount.incrementAndGet()
    }
}
