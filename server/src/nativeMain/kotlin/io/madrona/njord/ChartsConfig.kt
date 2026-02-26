package io.madrona.njord

import kotlinx.serialization.Serializable

@Serializable
data class ChartsConfig(
    val adminKey: String,
    val adminUser: String,
    val adminPass: String,
    val adminExpirationSeconds: Long,
    val pgUser: String,
    val pgPassword: String,
    val pgHost: String,
    val pgPort: Int,
    val pgConnectionPoolSize: Int,
    val pgDatabase: String,
    val host: String,
    val port: Int,
    private val externalScheme: String,
    private val externalHostName: String,
    private val externalPort: Int,
    val consoleMetrics: Boolean,
    val chartTempData: String,
    val webStaticContent: String,
    val shallowDepth: Float,
    val safetyDepth: Float,
    val deepDepth: Float,
    val debugTile: Boolean,
    val chartIngestWorkers: Int,
) {
    val externalBaseUrl = "${externalScheme}://${externalHostName}:${externalPort}"
}
