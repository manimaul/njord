@file:OptIn(ExperimentalAtomicApi::class)

package io.madrona.njord.ingest

import File
import OgrShapefileDataset
import ZipFile
import io.madrona.njord.Singletons
import io.madrona.njord.db.BaseFeatureDao
import io.madrona.njord.db.TileDao
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.util.DistributedLock
import io.madrona.njord.util.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class NaturalEarthIngest(
    private val ingestStatus: IngestStatus = Singletons.ingestStatus,
    val distributedLock: DistributedLock = Singletons.distributedLock,
    private val baseFeatureDao: BaseFeatureDao = Singletons.baseFeatureDao,
    private val tileDao: TileDao = Singletons.tileDao,
    val chartDir: File,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val log = logger()

    suspend fun ingest(encUpload: EncUpload) {
        if (distributedLock.lockAcquired) {
            runCatching {
                val report = ingestInternal(encUpload)
                log.info("Natural Earth ingestion complete $encUpload")
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
            println("NE ingest noop $encUpload - lock not acquired")
        }
    }

    private suspend fun ingestInternal(encUpload: EncUpload): Report {
        log.info("ingesting Natural Earth upload $encUpload")
        val shpFiles = step1UnzipFiles(encUpload)
            .filter {
                it.name.endsWith(".shp", ignoreCase = true)
            }.map {
                OgrShapefileDataset(it)
            }
        log.info("found ${shpFiles.size} shapefile(s)")

        log.info("counting features")
        val featureCount = shpFiles.sumOf { it.featureCount() }
        log.info("total feature count: $featureCount")

        val report = Report(totalChartCount = shpFiles.size, totalFeatureCount = featureCount)
        ingestStatus.writeMsg(report.progressMessage())

        shpFiles.forEach { shpFile ->
            if (!distributedLock.lockAcquired) {
                report.abort()
                return report
            }
            processShapefile(shpFile, report)
            report.completedChart()
        }

        return report
    }

    private fun step1UnzipFiles(encUpload: EncUpload): List<File> {
        ingestStatus.writeMsg(WsMsg.Extracting(0f))
        val retVal = mutableListOf<File>()
        File(chartDir, encUpload.zipFile).takeIf { it.exists() }?.let { zipFile ->
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
                        ingestStatus.writeMsg(WsMsg.Extracting((complete / size).toFloat()))
                        retVal.add(File(chartDir, name))
                    }
                }
            }
        } ?: run {
            log.error("chart dir file does not exist $chartDir/${encUpload.zipFile}")
            null
        }
        ingestStatus.writeMsg(WsMsg.Extracting(1f))
        return retVal
    }

    private suspend fun processShapefile(shpFile: OgrShapefileDataset, report: Report) {
        val fileName = shpFile.file.name

        val scale = scaleFromFilename(fileName)
        baseFeatureDao.deleteByNameAndScaleAsync(fileName, scale)

        shpFile.layerNames().mapNotNull {
            shpFile.getLayer(it)
        }.forEach { layer ->
            if (!distributedLock.lockAcquired) {
                report.abort()
                return
            }
            val layerName = layer.name?.let { normalizeLayerName(it) } ?: "unknown"
            var count = 0
            layer.features.forEach { feature ->
                if (!distributedLock.lockAcquired) {
                    report.abort()
                    return
                }
                val wkb = feature.geometry?.makeValid()?.wkb ?: return@forEach
                baseFeatureDao.insertAsync(wkb, feature.properties, fileName, scale, layerName)
                count++
            }

            log.info("inserted $count feature(s) for $fileName -> $layerName")
            report.appendChartFeatureCount(fileName, count.toLong())
            ingestStatus.writeMsg(report.progressMessage())
        }
    }

    companion object {
        private val resolutionRegex = Regex("^ne_(10|50|110)m_")

        fun normalizeLayerName(filename: String): String {
            val baseName = filename.substringBeforeLast(".")
            val suffix = baseName.replaceFirst(resolutionRegex, "")
            if (suffix.startsWith("bathymetry")) return "bathymetry"
            if (suffix.startsWith("lakes")) return "lakes"
            return suffix
        }

        fun scaleFromFilename(filename: String): Int {
            return when {
                filename.contains("_110m_") -> 110_000_000
                filename.contains("_50m_") -> 50_000_000
                else -> 10_000_000
            }
        }
    }
}
