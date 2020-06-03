package io.madrona.njord

import com.typesafe.config.Config
import java.net.InetSocketAddress
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class NjordConfig @Inject constructor(@Named("njord") config: Config) {
    val address: String = config.getString("address")
    val port: Int = config.getInt("port")
    val bossThreads: Int = config.getInt("bossThreads")
    val workerThreads: Int = config.getInt("workerThreads")
    val maxConnBacklog = config.getInt("maxConnBacklog")
    val connTimeout = config.getInt("connTimeout")
    val writeBufferQueueSizeBytesLow = config.getInt("writeBufferQueueSizeBytesLow")
    val writeBufferQueueSizeBytesHigh = config.getInt("writeBufferQueueSizeBytesHigh")
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
