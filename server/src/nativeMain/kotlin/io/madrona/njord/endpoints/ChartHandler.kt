package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.RegionDao
import io.madrona.njord.db.TileDao
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ingest.RegionExportWorker
import io.madrona.njord.ingest.RegionExporter
import io.madrona.njord.model.ChartInsert

class ChartHandler(
    private val dao: ChartDao = ChartDao(),
    private val regionDao: RegionDao = RegionDao(),
    private val tileDao: TileDao = Singletons.tileDao,
    private val config: ChartsConfig = Singletons.config,
    private val worker: RegionExportWorker = Singletons.regionExportWorker,
) : KtorHandler {
    override val route = "/v1/chart"

    override suspend fun handleGet(call: ApplicationCall) {
        call.request.queryParameters["id"]?.toLongOrNull()?.let {
            dao.findAsync(it)?.let { chart ->
                call.respond(chart)
            } ?: call.respond(HttpStatusCode.NotFound)
        } ?: call.respond(HttpStatusCode.BadRequest)
    }

    override suspend fun handlePost(call: ApplicationCall) = call.requireSignature {
        val chart = call.receive<ChartInsert>()
        dao.insertAsync(chart, true)?.let {
            call.respond(it)
        } ?: call.respond(HttpStatusCode.BadRequest)
    }

    /**
     * Deletes by `id`, or by `name` (the S-57 `DSID_DSNM`) - enc_cron diffs NOAA's catalog against
     * chart names and never learns Njord's row ids.
     */
    override suspend fun handleDelete(call: ApplicationCall) = call.requireSignature {
        val params = call.request.queryParameters
        val name = params["name"] ?: params["id"]?.toLongOrNull()?.let {
            dao.findAsync(it)?.name ?: return@requireSignature call.respond(HttpStatusCode.NoContent)
        }
        when (name?.let { delete(it) }) {
            true -> call.respond(HttpStatusCode.Accepted)
            false -> call.respond(HttpStatusCode.NoContent)
            null -> call.respond(HttpStatusCode.BadRequest)
        }
    }

    /**
     * Deletes the chart along with everything downstream that would otherwise keep serving it:
     * cached tiles drawn from it, and the export state of any region archive that embeds it.
     *
     * Which regions those are has to be resolved *before* the delete - afterwards there is no
     * coverage geometry left to intersect. The world base map is excluded because it carries no
     * chart data, and re-rendering it is expensive.
     */
    private suspend fun delete(name: String): Boolean? {
        val regions = regionDao.regionsContainingChart(
            name,
            config.regionExports
                .filter { it.name != RegionExporter.WORLD_REGION_NAME }
                .map { it.name to it.coverage },
        ) ?: emptyList()

        val deleted = dao.deleteByNameAsync(name)
        if (deleted == true) {
            tileDao.invalidateCache()
            regions.forEach { regionDao.clearRegionExportState(it) }
            if (regions.isNotEmpty()) worker.wake()
        }
        return deleted
    }
}
