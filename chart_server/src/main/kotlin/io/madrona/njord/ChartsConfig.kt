package io.madrona.njord

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class ChartsConfig(
        config: Config = ConfigFactory.load().getConfig("charts")
) {
    private val externalScheme: String = config.getString("externalScheme")
    private val externalHostName: String = config.getString("externalHostName")
    private val externalPort: Int = config.getInt("externalPort")

    val chartMinZoom: Int = config.getInt("chartMinZoom")
    val chartMaxZoom: Int = config.getInt("chartMaxZoom")
    val externalBaseUrl = "${externalScheme}://${externalHostName}:${externalPort}"
}
