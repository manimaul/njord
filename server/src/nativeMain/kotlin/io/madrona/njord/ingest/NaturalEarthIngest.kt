@file:OptIn(ExperimentalAtomicApi::class)

package io.madrona.njord.ingest

import File
import OgrShapefileDataset
import ZipFile
import io.madrona.njord.Singletons
import io.madrona.njord.db.BaseFeatureDao
import io.madrona.njord.db.TileDao
import io.madrona.njord.ext.jsonStr
import io.madrona.njord.geojson.FeatureCollection
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.util.DistributedLock
import io.madrona.njord.util.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
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
                        ingestStatus.writeMsg(WsMsg.Extracting((complete / size).toFloat()))
                        retVal.add(File(chartDir, name))
                    }
                }
            }
        }
        ingestStatus.writeMsg(WsMsg.Extracting(1f))
        return retVal
    }

    private suspend fun processShapefile(shpFile: OgrShapefileDataset, report: Report) {
        val fileName = shpFile.file.name
        val s57LayerName = s57Layer(fileName) ?: run {
            log.info("skipping unmapped shapefile: $fileName")
            return
        }
        log.info("processing $fileName -> $s57LayerName")


        val scale = scaleFromFilename(fileName)
        baseFeatureDao.deleteByNameAndScaleAsync(fileName, scale)

        shpFile.layerNames().mapNotNull {
            shpFile.getLayer(it)
        }.forEach { layer ->
            if (!distributedLock.lockAcquired) {
                report.abort()
                return
            }
            val geoJson = layer.geoJson()
            val enrichedGeoJson = if (s57LayerName == "DEPARE") {
                enrichDepareFeatures(geoJson, fileName)
            } else {
                geoJson
            }

            if (enrichedGeoJson.features.isEmpty()) {
                log.info("no features in $fileName, skipping insert")
                return
            }


            var count = 0
            enrichedGeoJson.features.forEach { feature ->
                if (!distributedLock.lockAcquired) {
                    report.abort()
                    return
                }
                val geomJson = feature.geometry?.jsonStr() ?: return@forEach
                val propsJson = feature.properties.jsonStr()
                baseFeatureDao.insertAsync(geomJson, propsJson, fileName, scale, s57LayerName)
                count++
            }

            log.info("inserted $count feature(s) for $fileName -> $s57LayerName")
            report.appendChartFeatureCount(fileName, count)
            ingestStatus.writeMsg(report.progressMessage())
        }
    }

    private fun enrichDepareFeatures(geoJson: FeatureCollection, baseName: String): FeatureCollection {
        val (drval1, drval2) = if (baseName.endsWith("_ocean")) {
            200.0 to 9999.0
        } else {
            // ne_10m_bathymetry_K_200 -> depth = 200; treat depth as DRVAL1
            val depth = baseName.split("_").lastOrNull()?.toDoubleOrNull() ?: 0.0
            depth to 9999.0
        }
        val enriched = geoJson.features.map { feature ->
            feature.copy(
                properties = buildJsonObject {
                    feature.properties.forEach { (k, v) -> put(k, v) }
                    put("DRVAL1", JsonPrimitive(drval1))
                    put("DRVAL2", JsonPrimitive(drval2))
                }
            )
        }
        return geoJson.copy(features = enriched)
    }

    companion object {
        private val resolutionRegex = Regex("^ne_(10|50|110)m_")

        private val layerMap = mapOf(
            "coastline" to "COALNE",
            "land" to "LNDARE",
            "minor_islands" to "LNDARE",
            "ocean" to "DEPARE",
            "lakes" to "LAKSHR",
            "rivers_lake_centerlines" to "RIVERS",
            "glaciated_areas" to "ICEARE",
            "reefs" to "SBDARE",
            "playas" to "SBDARE",
            "antarctic_ice_shelves_polys" to "ICEARE",
            "geographic_lines" to "DEPCNT",
        )

        fun s57Layer(filename: String): String? {
            val baseName = filename.substringBeforeLast(".")
            val suffix = baseName.replaceFirst(resolutionRegex, "")
            layerMap[suffix]?.let { return it }
            if (suffix.startsWith("bathymetry_")) return "DEPARE"
            return null
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
