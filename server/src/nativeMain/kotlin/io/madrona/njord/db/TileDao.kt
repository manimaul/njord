package io.madrona.njord.db

import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.geo.TileEncoder
import io.madrona.njord.model.ChartFeatureInfo
import io.madrona.njord.util.logger
import kotlin.time.measureTimedValue

class TileDao(
    private val chartsConfig: ChartsConfig = Singletons.config,
) {


    val log = logger()

//    private val cache = XMemcachedClientBuilder(listOf(InetSocketAddress(chartsConfig.memcacheHost, 11211))).also {
//            it.connectTimeout = 1000
//            it.isEnableHealSession = true
//            it.healSessionInterval = 2000
//            it.commandFactory = BinaryCommandFactory()
//            it.transcoder = SerializingTranscoder(10*1024*1024)   // memcached -I 10M -m 1024
//        }.build()

    fun clearCache() {
//        cache.flushAll()
//        counter.dec(counter.count)
    }

    fun cacheStatsMap() = emptyMap<String, Map<String, String>>() //= cache.stats

    fun logStats() {
//        log.info( "${cache.stats}")
    }

    suspend fun getTileInfo(z: Int, x: Int, y: Int): List<ChartFeatureInfo> {
        return TileEncoder(x, y, z).let {
            it.addCharts(true)
            it.infoJson()
        }
    }

    suspend fun getTile(z: Int, x: Int, y: Int): ByteArray {
        val (result, duration) = measureTimedValue {
            TileEncoder(x, y, z).let {
                it.addCharts(chartsConfig.debugTile)
                it.addDebug()
                it.encode()
            }
        }
        println("Tile creation $z,$x,$y took: $duration")
        return result
    }
}
