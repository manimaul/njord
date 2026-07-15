@file:OptIn(ExperimentalForeignApi::class)

package io.madrona.njord

import DataSource
import File
import PgDataSource
import TileSystem
import io.ktor.util.logging.*
import io.madrona.njord.db.BaseFeatureDao
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.db.TileCache
import io.madrona.njord.db.TileDao
import io.madrona.njord.endpoints.AdminUtil
import io.madrona.njord.geo.symbols.S57ObjectLibrary
import io.madrona.njord.ingest.IngestStatus
import io.madrona.njord.ingest.RegionExportWorker
import io.madrona.njord.layers.LayerFactory
import io.madrona.njord.model.ColorLibrary
import io.madrona.njord.util.DistributedLock
import io.madrona.njord.util.SpriteSheet
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import platform.posix.getenv
import kotlin.getValue

lateinit var resources: String

object Singletons {

    var genLog: Logger? = null

    val adminUtil by lazy { AdminUtil() }

    val baseFeatureDao by lazy { BaseFeatureDao() }

    val chartDao by lazy { ChartDao() }

    val featureDao by lazy { FeatureDao() }

    val tileCache by lazy { TileCache(File(config.chartTempData, "tiles")) }

    val chartUploadDir by lazy {
        File(config.chartTempData, "save")
    }

    val chartIngestWorkDir by lazy {
        File(config.chartTempData, "ingest")
    }

    val regionDir by lazy {
        File(config.chartTempData, "regions")
    }

    val ingestStatusFile by lazy {
        File(config.chartTempData, "status.json")
    }

    val lockFile by lazy {
        File(config.chartTempData, "lock")
    }

    val distributedLock by lazy {
        DistributedLock()
    }

    val migrationLockFile by lazy {
        File(config.chartTempData, "migration-lock")
    }

    // Separate from [distributedLock] (ingest/export) so a lock stranded by a killed
    // ingest or region-export can never block a new pod's startup migrations.
    val migrationLock by lazy {
        DistributedLock(lockFile = migrationLockFile)
    }

    val ingestStatus by lazy { IngestStatus() }

    val regionExportWorker by lazy { RegionExportWorker() }

    val tileDao by lazy { TileDao() }

    val config by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val baseJson = json.parseToJsonElement(
            File("$resources/config/application.json").readContents()
        ).jsonObject
        val merged = getenv("CHART_SERVER_OPTS")?.toKString()?.let { overrides ->
            JsonObject(baseJson + json.parseToJsonElement(overrides).jsonObject)
        } ?: baseJson
        json.decodeFromJsonElement<ChartsConfig>(merged)
    }

    val spriteSheet by lazy { SpriteSheet() }

    val ds: DataSource by lazy {
        "postgresql://"
        PgDataSource(
            config.pgConnectionInfo
        )
    }

    val colorLibrary by lazy { ColorLibrary() }

    val tileSystem by lazy { TileSystem() }

    val layerFactory by lazy { LayerFactory() }

    val s57ObjectLibrary by lazy { S57ObjectLibrary() }
}
