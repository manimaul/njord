package io.madrona.njord.db

import com.codahale.metrics.Counter
import com.codahale.metrics.Timer
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.geo.TileEncoder
import io.madrona.njord.model.ChartFeatureInfo
import io.madrona.njord.util.logger
import net.rubyeye.xmemcached.XMemcachedClientBuilder
import net.rubyeye.xmemcached.command.BinaryCommandFactory
import net.rubyeye.xmemcached.transcoders.SerializingTranscoder
import java.net.InetSocketAddress

class TileDao(
    private val chartsConfig: ChartsConfig = Singletons.config,
    private val timer: Timer = Singletons.metrics.timer("TileCacheFetch"),
    private val counter: Counter = Singletons.metrics.counter("TileCacheCount"),
) {


    val log = logger()

    private val cache = XMemcachedClientBuilder(listOf(InetSocketAddress(chartsConfig.memcacheHost, 11211))).also {
            it.connectTimeout = 1000
            it.isEnableHealSession = true
            it.healSessionInterval = 2000
            it.commandFactory = BinaryCommandFactory()
            it.transcoder = SerializingTranscoder(10*1024*1024)   // memcached -I 10M -m 1024
        }.build()

    fun clearCache() {
        cache.flushAll()
        counter.dec(counter.count)
    }

    fun logStats() {
        log.info( "${cache.stats}")
    }

    suspend fun getTileInfo(z: Int, x: Int, y: Int): List<ChartFeatureInfo> {
        return TileEncoder(x, y, z)
            .addCharts(true)
            .infoJson()
    }

    suspend fun getTile(z: Int, x: Int, y: Int): ByteArray {
        if (chartsConfig.debugTile) {
            return TileEncoder(x, y, z)
                .addCharts(false)
                .addDebug()
                .encode()
        }

        val key = "$z-$x-$y"
        val ctx = timer.time()

        return cache.get<ByteArray?>(key)?.let {
            cache.touch(key, 0)
            log.info("tile $key fetched from cache")
            ctx.stop()
            it
        } ?: TileEncoder(x, y, z)
            .addCharts(false)
            .encode().also {
                val added = cache.set(key, 0, it)
                log.debug("tile $key added $added")
                counter.inc()
            }
    }
}
