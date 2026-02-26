package io.madrona.njord.db

import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.geo.TileEncoder
import io.madrona.njord.model.ChartFeatureInfo
import io.madrona.njord.util.logger
import kotlin.time.measureTimedValue

class TileDao(
    private val chartsConfig: ChartsConfig = Singletons.config,
    private val tileCache: TileCache = Singletons.tileCache,
) {

    val log = logger()

    suspend fun getTileInfo(z: Int, x: Int, y: Int): List<ChartFeatureInfo> {
        return TileEncoder(x, y, z).let {
            it.addCharts(true)
            it.infoJson()
        }
    }

    suspend fun getTile(z: Int, x: Int, y: Int): ByteArray {
        val cached: Boolean
        val (result, duration) = measureTimedValue {
            val cacheTile = if (!chartsConfig.debugTile) {
                tileCache.get(z, x, y)
            } else {
                null
            }
            if (cacheTile == null) {
                cached = false
                TileEncoder(x, y, z).let {
                    it.addCharts(chartsConfig.debugTile)
                    if (chartsConfig.debugTile) {
                        it.addDebug()
                    }
                    it.encode()
                }
            } else {
                cached = true
                cacheTile
            }
        }
        println("Tile creation $z,$x,$y took: $duration, cached $cached")
        if (!chartsConfig.debugTile && !cached) {
            tileCache.put(z, x, y, result)
        }
        return result
    }

    fun invalidateCache() {
        tileCache.clear()
    }
}
