package io.madrona.njord

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.madrona.njord.model.ColorLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.gdal.osr.SpatialReference
import javax.sql.DataSource

object Singletons {

    val objectMapper: JsonMapper = jsonMapper {
        addModule(kotlinModule())
    }

    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val config = ChartsConfig()

    val ds: DataSource by lazy {
        val hc = HikariConfig()
        hc.jdbcUrl = "jdbc:postgresql://${config.pgHost}:${config.pgPort}/${config.pgDatabase}"
        hc.username = config.pgUser
        hc.password = config.pgPassword
        hc.addDataSourceProperty("cachePrepStmts", "true")
        hc.addDataSourceProperty("prepStmtCacheSize", "250")
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        HikariDataSource(hc)
    }

    val colorLibrary: ColorLibrary = ColorLibrary()

    val wgs84SpatialRef = SpatialReference("""GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563,AUTHORITY["EPSG","7030"]],AUTHORITY["EPSG","6326"]],PRIMEM["Greenwich",0,AUTHORITY["EPSG","8901"]],UNIT["degree",0.0174532925199433,AUTHORITY["EPSG","9122"]],AXIS["Latitude",NORTH],AXIS["Longitude",EAST],AUTHORITY["EPSG","4326"]]""")
}