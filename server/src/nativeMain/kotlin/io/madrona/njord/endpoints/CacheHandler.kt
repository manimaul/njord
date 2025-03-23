package io.madrona.njord.endpoints

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.db.TileDao
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.CacheInfo
import kotlinx.coroutines.delay

class CacheHandler(
    private val tileDao: TileDao = Singletons.tileDao
) : KtorHandler {
    override val route = "/v1/cache"

    override suspend fun handlePost(call: ApplicationCall) {
        tileDao.clearCache()
        delay(100)
        call.respond(getInfo())
    }

    override suspend fun handleGet(call: ApplicationCall) {
        call.respond(getInfo())
    }

    private fun getInfo(): CacheInfo {

        val stats = tileDao.cacheStatsMap()
        val count = stats.asSequence().fold(0L) { acc, entry ->
            acc + (entry.value["curr_items"]?.toLong() ?: 0L)
        }
        return CacheInfo(
            connections = stats.size,
            currentItemCount = count
        )
    }
}