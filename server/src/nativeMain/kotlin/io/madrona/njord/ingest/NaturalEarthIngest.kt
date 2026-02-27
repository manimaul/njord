@file:OptIn(ExperimentalAtomicApi::class)

package io.madrona.njord.ingest

import File
import OgrShapefileDataset
import ZipFile
import io.madrona.njord.Singletons
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.GeoJsonDao
import io.madrona.njord.db.TileDao
import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.FeatureCollection
import io.madrona.njord.geojson.Polygon
import io.madrona.njord.geojson.Position
import io.madrona.njord.model.ChartInsert
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.FeatureInsert
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
    private val chartDao: ChartDao = ChartDao(),
    private val geoJsonDao: GeoJsonDao = GeoJsonDao(),
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

    private suspend fun ingestInternal(encUpload: EncUpload): NeReport {
        log.info("ingesting Natural Earth upload $encUpload")
        val shpFiles = step1UnzipFiles(encUpload).filter { it.name.endsWith(".shp", ignoreCase = true) }
        log.info("found ${shpFiles.size} shapefile(s)")

        val report = NeReport(totalShapefileCount = shpFiles.size)
        ingestStatus.writeMsg(report.progressMessage())

        shpFiles.forEach { shpFile ->
            if (!distributedLock.lockAcquired) {
                report.abort()
                return report
            }
            processShapefile(shpFile, report)
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

    private suspend fun processShapefile(shpFile: File, report: NeReport) {
        val baseName = shpFile.name.substringBeforeLast(".")
        val s57LayerName = s57Layer(shpFile.name) ?: run {
            log.info("skipping unmapped shapefile: ${shpFile.name}")
            return
        }
        log.info("processing $baseName -> $s57LayerName")

        val worldCovr = Feature(
            geometry = Polygon(
                coordinates = listOf(
                    listOf(
                        Position(-180.0, -90.0),
                        Position(180.0, -90.0),
                        Position(180.0, 90.0),
                        Position(-180.0, 90.0),
                        Position(-180.0, -90.0)
                    )
                )
            )
        )

        val chartInsert = ChartInsert(
            name = baseName,
            scale = scaleFromFilename(shpFile.name),
            fileName = shpFile.name,
            updated = "",
            issued = "",
            zoom = 0,
            covr = worldCovr,
            dsidProps = emptyMap(),
            chartTxt = emptyMap(),
            isBasemap = true,
        )

        val chart = chartDao.insertAsync(chartInsert, overwrite = true) ?: run {
            log.error("failed to insert chart for $baseName")
            report.fail(baseName, "chart insert failed")
            return
        }

        val layer = try {
            OgrShapefileDataset(shpFile).getLayerAt(0)
        } catch (e: IllegalArgumentException) {
            log.error("failed to open shapefile ${shpFile.name}: ${e.message}")
            report.fail(baseName, "open failed: ${e.message}")
            return
        } ?: run {
            log.error("no layer in shapefile: ${shpFile.name}")
            report.fail(baseName, "no layer found")
            return
        }

        val geoJson = layer.geoJson()
        val enrichedGeoJson = if (s57LayerName == "DEPARE") {
            enrichDepareFeatures(geoJson, baseName)
        } else {
            geoJson
        }

        if (enrichedGeoJson.features.isEmpty()) {
            log.info("no features in $baseName, skipping insert")
            return
        }

        val count = geoJsonDao.featureInsertAsync(
            FeatureInsert(layerName = s57LayerName, chart = chart, geo = enrichedGeoJson)
        ) ?: 0

        log.info("inserted $count feature(s) for $baseName -> $s57LayerName")
        report.append(baseName, count)
        ingestStatus.writeMsg(report.progressMessage())
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
            val baseName = filename.substringBeforeLast(".")
            return when {
                baseName.contains("_110m_") -> 110_000_000
                baseName.contains("_50m_") -> 50_000_000
                else -> 10_000_000
            }
        }
    }
}

private class NeReport(
    val totalShapefileCount: Int,
) {
    private val aborted = AtomicInt(0)
    private val featureCount = AtomicLong(0)
    private val shapefilesDone = AtomicInt(0)
    private val insertItems: MutableMap<String, Int> = mutableMapOf()
    private val failed: MutableMap<String, String> = mutableMapOf()
    private val time = Clock.System.now().toEpochMilliseconds()

    private fun elapsed() = Clock.System.now().toEpochMilliseconds() - time

    fun abort(): NeReport {
        aborted.value = 1
        return this
    }

    fun progressMessage() = WsMsg.Info(
        feature = featureCount.value,
        chart = shapefilesDone.value,
        totalCharts = totalShapefileCount,
        totalFeatures = 0L,
    )

    fun completionMessage() = if (aborted.value == 1) WsMsg.Idle else WsMsg.CompletionReport(
        totalFeatureCount = featureCount.value,
        totalChartCount = totalShapefileCount,
        failedCharts = failed.map { "${it.key} msg: ${it.value}" },
        items = insertItems.map { WsMsg.InsertItem(it.key, it.value) },
        ms = elapsed()
    )

    fun append(name: String, count: Int) {
        featureCount.getAndAdd(count.toLong())
        insertItems[name] = (insertItems[name] ?: 0) + count
        shapefilesDone.incrementAndGet()
    }

    fun fail(name: String, msg: String) {
        failed[name] = msg
    }
}
