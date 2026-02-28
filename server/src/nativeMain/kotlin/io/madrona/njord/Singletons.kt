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

    val ingestStatusFile by lazy {
        File(config.chartTempData, "status.json")
    }

    val lockFile by lazy {
        File(config.chartTempData, "lock")
    }

    val distributedLock by lazy {
        DistributedLock()
    }

    val ingestStatus by lazy { IngestStatus() }

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
        PgDataSource(
            "postgresql://${config.pgUser}:${config.pgPassword}@${config.pgHost}:${config.pgPort}/${config.pgDatabase}" +
                    "?keepalives=1&keepalives_idle=30&keepalives_interval=10&keepalives_count=5",
            config.pgConnectionPoolSize
        )
    }

    val colorLibrary by lazy { ColorLibrary() }

    val tileSystem by lazy { TileSystem() }

    val layerFactory by lazy { LayerFactory() }

    val s57ObjectLibrary by lazy { S57ObjectLibrary() }
}
