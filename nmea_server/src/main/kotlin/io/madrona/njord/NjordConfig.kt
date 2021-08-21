package io.madrona.njord

import com.typesafe.config.Config
import com.willkamp.vial.api.VialConfig
import java.net.InetSocketAddress
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class NjordConfig @Inject constructor(
        @Named("njord") config: Config,
        vialConfig: VialConfig
) {
    val address: String = vialConfig.address
    val port: Int = vialConfig.port
    val commPorts: List<String> = Collections.unmodifiableList(config.getStringList("commPorts"))
    val bauds: Set<Int> = Collections.unmodifiableSet(HashSet(config.getIntList("commBauds")))
    val tcpNmeaRelay: Set<InetSocketAddress> = addresses(config.getStringList("tcpNmeaRelay"))

    private val log = logger()

    private fun addresses(list: List<String>) :Set<InetSocketAddress> {
        return list.mapNotNull {hostPort ->
            val split = hostPort.split(":")
            val host = split.firstOrNull()?.takeIf { it.isNotBlank() }
            val port = split.lastOrNull()?.toIntOrNull()
            if (host != null && port != null) {
                InetSocketAddress(host, port)
            } else {
                log.warn("invalid tcpNmeaRelay declaration <${hostPort}>")
                null
            }
        }.toSet()
    }
}
