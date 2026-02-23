package io.madrona.njord

import DataSource
import File
import PgDataSource
import TileSystem
import io.ktor.util.logging.*
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.db.TileDao
import io.madrona.njord.endpoints.AdminUtil
import io.madrona.njord.geo.symbols.S57ObjectLibrary
import io.madrona.njord.layers.LayerFactory
import io.madrona.njord.model.ColorLibrary
import io.madrona.njord.util.SpriteSheet
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlin.getValue

lateinit var resources: String

object Singletons {

    var genLog: Logger? = null

    val adminUtil by lazy { AdminUtil() }

    val chartDao by lazy { ChartDao() }

    val featureDao by lazy { FeatureDao() }

    val tileDao by lazy { TileDao() }

    val config by lazy {
        val contents = File("$resources/config/application.json")
        decodeFromString<ChartsConfig>(contents.readContents())
    }

    val spriteSheet by lazy { SpriteSheet() }

    val ds: DataSource by lazy {
        PgDataSource("postgresql://${config.pgUser}:${config.pgPassword}@${config.pgHost}:${config.pgPort}/${config.pgDatabase}", config.pgConnectionPoolSize)
    }

    val colorLibrary by lazy { ColorLibrary() }

    val tileSystem by lazy { TileSystem() }

    val layerFactory by lazy { LayerFactory() }

    val s57ObjectLibrary by lazy { S57ObjectLibrary() }
}
