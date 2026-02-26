package io.madrona.njord.ingest

import File
import InsertError
import InsertSuccess
import OgrS57Dataset
import ZipFile
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.*
import io.madrona.njord.ext.letTwo
import io.madrona.njord.model.*
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.util.logger
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong

class ChartIngest(
    private val statusFile: File,
    private val config: ChartsConfig = Singletons.config,
    private val chartDao: ChartDao = ChartDao(),
    private val geoJsonDao: GeoJsonDao = GeoJsonDao(),
    private val tileDao: TileDao = Singletons.tileDao,
    val chartDir: File,
) {

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
                OgrS57Dataset(file).featureCount(exLayers)
            }
        }

        log.info("counting features sum = $featureCount")
        val report = Report(
            totalFeatureCount = featureCount,
            totalChartCount = s57Files.size
        )
        statusFile.writeMsg(report.progressMessage())
        log.info("inserting data")
        val queue = ArrayDeque(s57Files)
        working.getAndSet(0)
        val chartsWorking = AtomicInt(0)
        while (queue.isNotEmpty()) {
            if (chartsWorking.value < config.chartIngestWorkers) {
                queue.removeFirstOrNull()?.let { file ->
                    val w = chartsWorking.incrementAndGet()
                    log.info("inserting chart ${file.name} working = $w")
                    OgrS57Dataset(file).let { s57 ->
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
            } else {
                delay(250)
            }
        }
        statusFile.writeMsg(report.completionMessage())

        log.info("cleaning up resources")
        withContext(Dispatchers.IO) {
            chartDir.deleteRecursively()
        }
        tileDao.invalidateCache()
        statusFile.writeMsg(WsMsg.Idle)
    }

    private suspend fun step1UnzipFiles(
        encUpload: EncUpload,
    ): List<File> {
        statusFile.writeMsg(WsMsg.Extracting(0f))
        val retVal = mutableListOf<File>()
        val files = encUpload.zipFiles.mapNotNull { name ->
            File(chartDir, name).takeIf { it.exists() } ?: run {
                log.error("chart dir file does not exist $chartDir/$name")
                null
            }
        }
        files.forEach { zipFile ->
            ZipFile(zipFile).let { zip ->
                val size = zip.size().toDouble()
                var complete = 0.0
                zip.entries().filter { !it.isDirectory() }.forEach { entry ->
                    val name = entry.name()
                    if (!name.startsWith("__MACOSX")) {
                        entry.unzipToPath(chartDir)
                        complete += 1
                        statusFile.writeMsg(
                            WsMsg.Extracting((complete / size).toFloat())
                        )
                        retVal.add(File(chartDir, name))
                    }
                }
            }
        }
        statusFile.writeMsg(WsMsg.Extracting(1f))
        return retVal
    }

    private fun chartInsertData(
        s57: OgrS57Dataset, report: Report
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
        s57: OgrS57Dataset,
        chart: Chart, report: Report
    ) {
        val queue = ArrayDeque(s57.layerNames().filter { !exLayers.contains(it) })
        withContext(Dispatchers.IO) {
            while (queue.isNotEmpty()) {
                if (working.value < config.featureIngestWorkers) {
                    queue.removeFirstOrNull()?.let { layerName ->
                        s57.getLayer(layerName)?.let { layer ->
                            val w = working.incrementAndGet()
                            log.info("$layerName ${layer.features.size} feature(s) inserting working=$w remaining=${queue.size}")
                            layer.geoJson().takeIf { it.features.isNotEmpty() }?.let { geo ->
                                val count = geoJsonDao.featureInsertAsync(
                                    FeatureInsert(
                                        layerName = layerName, chart = chart, geo = geo
                                    )
                                ) ?: 0
                                val w = working.decrementAndGet()
                                log.info("$layerName ${layer.features.size} feature(s) insert complete working=$w remaining_layers=${queue.size}")
                                if (count == 0) {
                                    log.debug("error inserting feature with layer = $layerName geo feature count = ${geo.features.size}")
                                    log.debug("geo json = \n${geo}")
                                }
                                report.appendChartFeatureCount(chart.name, count)
                            }
                            statusFile.writeMsg(report.progressMessage())
                        }
                    }
                } else {
                    delay(250)
                }
            }
            report.completedChart()
            statusFile.writeMsg(report.progressMessage())
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
    private val insertItems: MutableMap<String, Int> = mutableMapOf()
    private val failedCharts: MutableMap<String, String> = mutableMapOf()
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
        val m = if (failedCharts.contains(name)) {
           "${failedCharts[name]}, $msg"
        } else {
            msg
        }
        failedCharts[name] = m
    }

    fun completedChart() {
        chartInstallCount.incrementAndGet()
    }
}
