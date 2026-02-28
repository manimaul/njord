@file:OptIn(ExperimentalAtomicApi::class)

package io.madrona.njord.ingest

import File
import InsertError
import InsertSuccess
import OgrS57Dataset
import ZipFile
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.*
import io.madrona.njord.model.*
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.util.DistributedLock
import io.madrona.njord.util.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class ChartIngest(
    private val ingestStatus: IngestStatus = Singletons.ingestStatus,
    val distributedLock: DistributedLock = Singletons.distributedLock,
    private val config: ChartsConfig = Singletons.config,
    private val chartDao: ChartDao = ChartDao(),
    private val geoJsonDao: GeoJsonDao = GeoJsonDao(),
    private val featureDao: FeatureDao = FeatureDao(),
    private val tileDao: TileDao = Singletons.tileDao,
    val chartDir: File,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val log = logger()

    suspend fun ingest(encUpload: EncUpload) {
        if (distributedLock.lockAcquired) {
            runCatching {
                val report = ingestInternal(encUpload)
                log.info("ingestion complete $encUpload")
                log.info("cleaning up resources")
                tileDao.invalidateCache()
                chartDir.deleteRecursively()
                if (!distributedLock.lockAcquired) {
                    report.abort()
                }
                ingestStatus.writeMsg(report.completionMessage())
            }
            distributedLock.tryClearLock()
        } else {
            println("ingest noop $encUpload - lock not acquired")
        }
    }

    private suspend fun ingestInternal(encUpload: EncUpload): Report {
        log.info("ingesting enc upload $encUpload")
        log.info("unzipping files")
        val s57Files = step1UnzipFiles(encUpload).filter {
            it.name.endsWith(".000")
        }.map { OgrS57Dataset(it) }

        log.info("counting features")
        val featureCount = s57Files.sumOf {
            log.info("summing features of: ${it.file}")
            it.featureCount(exLayers)
        }
        log.info("total feature count: $featureCount")

        val report = Report(
            totalFeatureCount = featureCount,
            totalChartCount = s57Files.size
        )
        ingestStatus.writeMsg(report.progressMessage())

        log.info("inserting data")
        val queue = ArrayDeque(s57Files)
        val chartsWorking = AtomicInt(0)
        val jobs = mutableListOf<Job>()
        while (queue.isNotEmpty()) {
            if (!distributedLock.lockAcquired) {
                report.abort()
                break
            }
            if (chartsWorking.value < config.chartIngestWorkers) {
                queue.removeFirstOrNull()?.let { s57 ->
                    val w = chartsWorking.incrementAndGet()
                    jobs.add(launch {
                        log.info("inserting chart ${s57.file.name} working = $w")
                        chartInsertData(s57, report)?.let { data ->
                            chartDao.insertAsync(data, true)?.let {
                                installChartFeatures(s57, it, report)
                            } ?: run {
                                report.failChart(s57.file.name, "chart insert")
                            }
                        }
                        chartsWorking.decrementAndGet()
                    })
                }
            } else {
                delay(250)
            }
        }
        jobs.joinAll()
        return report
    }

    private fun step1UnzipFiles(
        encUpload: EncUpload,
    ): List<File> {
        ingestStatus.writeMsg(WsMsg.Extracting(0f))
        val retVal = mutableListOf<File>()
        val files = encUpload.zipFiles.mapNotNull { name ->
            File(chartDir, name).takeIf { it.exists() } ?: run {
                log.error("chart dir file does not exist $chartDir/$name")
                null
            }
        }
        files.forEach { zipFile ->
            ZipFile(zipFile).let { zip ->
                if (!distributedLock.lockAcquired) {
                    return emptyList()
                }
                val size = zip.size().toDouble()
                var complete = 0.0
                zip.entries().filter { !it.isDirectory() }.forEach { entry ->
                    val name = entry.name()
                    if (!distributedLock.lockAcquired) {
                        return emptyList()
                    }
                    if (!name.startsWith("__MACOSX")) {
                        entry.unzipToPath(chartDir)
                        complete += 1
                        ingestStatus.writeMsg(
                            WsMsg.Extracting((complete / size).toFloat())
                        )
                        retVal.add(File(chartDir, name))
                    }
                }
            }
        }
        ingestStatus.writeMsg(WsMsg.Extracting(1f))
        return retVal
    }

    private suspend fun chartInsertData(
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
        chart: Chart,
        report: Report
    ) {
        s57.layerNames().filter { !exLayers.contains(it) }.forEach { layerName ->
            if (!distributedLock.lockAcquired) {
                report.abort()
                return
            }
            s57.getLayer(layerName)?.let { layer ->
                layer.features.forEach { feature ->
                    feature.geometry?.makeValid()?.wkb?.let { wkb ->
                        val count = featureDao.insertFeature(layerName, chart, wkb, feature.properties)
                        if (count == 0L) {
                            log.debug("error inserting feature with layer = $layerName")
                            log.debug("geo json = \n${feature.geometry?.geoJson()}")
                        }
                        report.appendChartFeatureCount(chart.name, count ?: 0)
                    }
                }
                ingestStatus.writeMsg(report.progressMessage())
            }
        }
        report.completedChart()
        ingestStatus.writeMsg(report.progressMessage())
    }

    companion object {
        val exLayers = setOf("DSID", "IsolatedNode", "ConnectedNode", "Edge", "Face")
    }
}

data class Report(
    val totalFeatureCount: Long,
    val totalChartCount: Int,
) {

    private val mutex = Mutex()
    private val aborted = AtomicInt(0)
    private val featureInstallCount = AtomicLong(0)
    private val chartInstallCount = AtomicInt(0)
    private val insertItems: MutableMap<String, Long> = mutableMapOf()
    private val failedCharts: MutableMap<String, String> = mutableMapOf()
    private val time = Clock.System.now().toEpochMilliseconds()
    private fun elapsed(): Long {
        return Clock.System.now().toEpochMilliseconds() - time
    }

    fun abort(): Report {
        println("result set to aborted")
        aborted.value = 1
        return this
    }

    fun progressMessage() = WsMsg.Info(
        feature = featureInstallCount.value,
        chart = chartInstallCount.value,
        totalCharts = totalChartCount,
        totalFeatures = totalFeatureCount,
    )

    fun completionMessage() = if (aborted.value == 1) WsMsg.Idle else WsMsg.CompletionReport(
        totalFeatureCount = totalFeatureCount,
        totalChartCount = totalChartCount,
        failedCharts = failedCharts.map { "${it.key} msg: ${it.value}" },
        items = insertItems.map { WsMsg.InsertItem(it.key, it.value) },
        ms = elapsed()
    )

    suspend fun appendChartFeatureCount(chartName: String, featureCount: Long) {
        featureInstallCount.getAndAdd(featureCount)
        mutex.withLock {
            insertItems[chartName] = (insertItems[chartName] ?: 0) + featureCount
        }
    }

    suspend fun failChart(name: String, msg: String) {
        mutex.withLock {
            val m = if (failedCharts.contains(name)) {
                "${failedCharts[name]}, $msg"
            } else {
                msg
            }
            failedCharts[name] = m
        }
    }

    fun completedChart() {
        chartInstallCount.incrementAndGet()
    }
}
