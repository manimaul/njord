package io.madrona.njord

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.File

class ChartsConfig(
        config: Config = ConfigFactory.load().getConfig("charts")
) {

    private val externalScheme: String = config.getString("externalScheme")
    private val externalHostName: String = config.getString("externalHostName")
    private val externalPort: Int = config.getInt("externalPort")

    val adminKey: String = config.getString("adminKey")
    val adminExpirationSeconds: Long = config.getLong("adminExpirationSeconds")
    val host: String = config.getString("host")
    val port: Int = config.getInt("port")

    val shallowDepth: Float = config.getDouble("shallowDepth").toFloat()
    val safetyDepth: Float = config.getDouble("safetyDepth").toFloat()
    val deepDepth: Float = config.getDouble("deepDepth").toFloat()

    val debugTile: Boolean = config.getBoolean("debugTile")

    val chartMinZoom: Int = config.getInt("chartMinZoom")
    val chartMaxZoom: Int = config.getInt("chartMaxZoom")

    val chartSymbolSprites: String = config.getString("chartSymbolSprites")

    val chartTempData: File = File(config.getString("chartTempData")).also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }

    val webStaticContent: File = File(config.getString("webStaticContent")).also {
        if (!it.exists() || !it.canRead()) {
            throw IllegalStateException("webStaticContent ${it.absolutePath} does not exist or is not readable")
        }
    }
    val externalBaseUrl = "${externalScheme}://${externalHostName}:${externalPort}"

    val pgUser: String = config.getString("pgUser")
    val pgPassword: String = config.getString("pgPassword")
    val pgHost: String = config.getString("pgHost")
    val pgPort: Int = config.getInt("pgPort")
    val pgConnectionPoolSize: Int = config.getInt("pgConnectionPoolSize")
    val pgDatabase: String = config.getString("pgDatabase")
}
