package io.madrona.njord

import com.typesafe.config.Config
import java.util.*
import javax.inject.Inject
import javax.inject.Named

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
}