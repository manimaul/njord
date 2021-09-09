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

    val chartMinZoom: Int = config.getInt("chartMinZoom")
    val chartMaxZoom: Int = config.getInt("chartMaxZoom")
    val chartTempData: File = File(config.getString("chartTempData")).also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
    val externalBaseUrl = "${externalScheme}://${externalHostName}:${externalPort}"
}
