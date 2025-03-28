package io.madrona.njord.ingest

import com.codahale.metrics.Timer
import io.ktor.websocket.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.*
import io.madrona.njord.ext.letTwo
import io.madrona.njord.geo.S57
import io.madrona.njord.model.*
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.model.ws.sendMessage
import io.madrona.njord.util.logger
import kotlinx.coroutines.*
import java.io.File
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile

class ChartIngest(
    private val webSocketSession: WebSocketSession,
    private val config: ChartsConfig = Singletons.config,
    private val chartDao: ChartDao = ChartDao(),
    private val geoJsonDao: GeoJsonDao = GeoJsonDao(),
    private val tileDao: TileDao = Singletons.tileDao,
    private val timer: Timer = Singletons.metrics.timer("ChartIngestFeatureInsert"),
) {

    private val charDir = config.chartTempData
    private val log = logger()
    private val working = AtomicInteger(0)

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
                S57.from(file)?.use {
                    it.featureCount(exLayers)
                } ?: 0
            }
        }
        val report = Report(
            totalFeatureCount = featureCount,
            totalChartCount = s57Files.size
        )
        webSocketSession.sendMessage(report.progressMessage())
        log.info("inserting data")
        val queue = ConcurrentLinkedDeque(s57Files)
        working.set(0)
        withContext(Dispatchers.IO) {
            val chartsWorking = AtomicInteger(0)
            while (queue.isNotEmpty()) {
                if (chartsWorking.get() < config.chartIngestWorkers) {
                    queue.poll()?.let { file ->
                        val w = chartsWorking.incrementAndGet()
                        launch {
                            log.info("inserting chart ${file.name} working = $w")
                            S57.from(file)?.use { s57 ->
                                chartInsertData(s57, report)?.let { data ->
                                    ConcurrentLinkedDeque(s57.layerGeoJsonSequence(exLayers).toList()) to data
                                }
                            }?.let { (queue, data) ->
                                chartDao.sqlOpAsync(tryCount = 2) { conn ->
                                    chartDao.insertAsync(data, true, conn)?.let {
                                        installChartFeatures(queue, it, report, conn)
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
        webSocketSession.sendMessage(
            WsMsg.Extracting(0f)
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
                                            WsMsg.Extracting(complete / size)
                                        )
                                    }
                                    retVal.add(outFile)
                                }
                            }
                        }
                    }
                }
            }
            retVal.also {
                webSocketSession.sendMessage(
                    WsMsg.Extracting(1f)
                )
            }
        } ?: emptyList()
    }

    private fun chartInsertData(
        s57: S57, report: Report
    ): ChartInsert? {
        return when (val insert = s57.chartInsertInfo()) {
            is InsertError -> {
                report.failChart(s57.file.name, insert.msg)
                null
            }

            is InsertSuccess -> {
                insert.value
            }
        }
    }

    private suspend fun installChartFeatures(
        queue: Queue<LayerGeoJson>,
        chart: Chart, report: Report, conn: Connection) {
        withContext(Dispatchers.IO) {
            while (queue.isNotEmpty()) {
                if (working.get() < config.featureIngestWorkers) {
                    working.incrementAndGet()
                    queue.poll()?.let { (layerName, geo) ->
                        launch(Dispatchers.IO) {
                            val ctx = timer.time()
                            val count = geoJsonDao.featureInsert(
                                FeatureInsert(
                                    layerName = layerName, chart = chart, geo = geo
                                ), conn
                            )
                            val ms = ctx.stop() / 1000L
                            val w = working.decrementAndGet()
                            if (count == 0) {
                                log.debug("error inserting feature with layer = $layerName geo feature count = ${geo.features.size}")
                                log.debug("geo json = \n${geo}")
                            }
                            log.info("$count feature(s) inserted in $ms working = ${w}")
                            report.appendChartFeatureCount(chart.name, count)
                            webSocketSession.sendMessage(report.progressMessage())
                        }
                    }
                }

            }
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
    val totalChartCount: Int,
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
        featureInstallCount.getAndUpdate { it + featureCount.toLong() }
        insertItems[chartName] = (insertItems[chartName] ?: 0) + featureCount
    }

    fun failChart(name: String, msg: String) {
        failedCharts.compute(name) { _, value -> value?.let { "$it, $msg" } ?: msg }
    }

    fun completedChart() {
        chartInstallCount.incrementAndGet()
    }
}
