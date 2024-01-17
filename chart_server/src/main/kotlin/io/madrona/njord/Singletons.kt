package io.madrona.njord

import com.codahale.metrics.ConsoleReporter
import com.codahale.metrics.MetricRegistry
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.db.TileDao
import io.madrona.njord.endpoints.AdminUtil
import io.madrona.njord.geo.TileSystem
import io.madrona.njord.geo.symbols.S57ObjectLibrary
import io.madrona.njord.layers.LayerFactory
import io.madrona.njord.model.ColorLibrary
import io.madrona.njord.util.SpriteSheet
import io.madrona.njord.util.ZFinder
import org.gdal.osr.SpatialReference
import org.locationtech.jts.geom.GeometryFactory
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

object Singletons {

    val adminUtil by lazy { AdminUtil() }

    val chartDao by lazy { ChartDao() }

    val featureDao by lazy { FeatureDao() }

    val tileDao by lazy { TileDao() }

    val objectMapper: JsonMapper by lazy {
        jsonMapper {
            addModule(kotlinModule())
        }
    }

    val config by lazy { ChartsConfig() }

    val spriteSheet by lazy { SpriteSheet() }

    val ds: DataSource by lazy {
        val hc = HikariConfig()
        hc.jdbcUrl = "jdbc:postgresql://${config.pgHost}:${config.pgPort}/${config.pgDatabase}"
        hc.username = config.pgUser
        hc.password = config.pgPassword
        hc.addDataSourceProperty("cachePrepStmts", "true")
        hc.addDataSourceProperty("prepStmtCacheSize", "250")
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        hc.maximumPoolSize = config.pgConnectionPoolSize
        hc.connectionTimeout = 120000
        hc.leakDetectionThreshold = 300000
        HikariDataSource(hc)
    }

    val colorLibrary by lazy { ColorLibrary() }

    val wgs84SpatialRef by lazy { SpatialReference("""GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563,AUTHORITY["EPSG","7030"]],AUTHORITY["EPSG","6326"]],PRIMEM["Greenwich",0,AUTHORITY["EPSG","8901"]],UNIT["degree",0.0174532925199433,AUTHORITY["EPSG","9122"]],AXIS["Latitude",NORTH],AXIS["Longitude",EAST],AUTHORITY["EPSG","4326"]]""") }

    val zFinder by lazy { ZFinder() }

    val tileSystem by lazy { TileSystem() }

    val geometryFactory by lazy { GeometryFactory() }

    val metrics by lazy {
        MetricRegistry().also {
            ConsoleReporter.forRegistry(it)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build()
                .start(5, TimeUnit.MINUTES)
        }
    }

    val layerFactory by lazy { LayerFactory() }

    val s57ObjectLibrary by lazy { S57ObjectLibrary() }
}
