package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.db.TileCache
import io.madrona.njord.ext.KtorHandler

class CacheHandler(
    private val tileCache: TileCache = Singletons.tileCache
) : KtorHandler {
    override val route = "/v1/cache"

    override suspend fun handleGet(call: ApplicationCall) {
        call.respond(tileCache.counts())
    }

    override suspend fun handleDelete(call: ApplicationCall) = call.requireSignature {
        tileCache.clear()
        call.respond(HttpStatusCode.NoContent)
    }
}
