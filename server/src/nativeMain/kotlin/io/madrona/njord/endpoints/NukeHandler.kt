package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.db.NukeDao
import io.madrona.njord.db.TileCache
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ingest.RegionExporter

/**
 * Wipes all ingested chart data (features, charts, base_features), the tile cache,
 * and all rendered region archives. Irreversible — requires a valid admin signature.
 */
class NukeHandler(
    private val dao: NukeDao = NukeDao(),
    private val tileCache: TileCache = Singletons.tileCache,
    private val regionExporter: RegionExporter = RegionExporter(),
) : KtorHandler {
    override val route = "/v1/nuke"

    override suspend fun handleDelete(call: ApplicationCall) = call.requireSignature {
        if (dao.truncateAllAsync()) {
            tileCache.clear()
            regionExporter.clear()
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
