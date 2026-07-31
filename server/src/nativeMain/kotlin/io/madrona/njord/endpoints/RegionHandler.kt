package io.madrona.njord.endpoints

import File
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.db.RegionDao
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ingest.RegionExportWorker
import io.madrona.njord.ingest.RegionExporter
import kotlinx.coroutines.*

/**
 * GET  /v1/regions — returns every region configured in [io.madrona.njord.ChartsConfig.regionExports]
 * as a [io.madrona.njord.model.RegionManifestEntry]; regions with no rendered archive yet still
 * appear, with `archive`/`createdAt` set to null.
 * POST /v1/regions?name={region} — admin: forces regeneration of the named region by deleting its
 * region_export_state row (making it stale) and waking the export worker. Responds 202; the
 * archive renders asynchronously and appears in the manifest when done.
 */
class RegionHandler(
    private val exporter: RegionExporter = RegionExporter(),
    private val config: ChartsConfig = Singletons.config,
    private val regionDao: RegionDao = RegionDao(),
    private val worker: RegionExportWorker = Singletons.regionExportWorker,
) : KtorHandler, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    override val route = "/v1/regions"

    override suspend fun handleGet(call: ApplicationCall) {
        call.respond(exporter.buildManifest())
    }

    override suspend fun handlePost(call: ApplicationCall) = call.requireSignature {
        val name = call.parameters["name"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "missing required query parameter: name")
            return@requireSignature
        }
        if (config.regionExports.none { it.name == name }) {
            call.respond(HttpStatusCode.NotFound, "no configured region named $name")
            return@requireSignature
        }
        if (regionDao.clearRegionExportState(name) == null) {
            call.respond(HttpStatusCode.InternalServerError)
            return@requireSignature
        }
        worker.wake()
        call.respond(HttpStatusCode.Accepted)
    }
}

/**
 * GET /v1/regions/{archive} — streams a region SQLite archive for download.
 */
class RegionArchiveHandler(
    private val regionDir: File = Singletons.regionDir,
) : KtorHandler {
    override val route = "/v1/regions/{archive}"

    override suspend fun handleGet(call: ApplicationCall) = call.requireSignature {
        val archiveName = call.parameters["archive"] ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return@requireSignature
        }
        // Prevent path traversal
        if (archiveName.contains('/') || archiveName.contains("..")) {
            call.respond(HttpStatusCode.BadRequest)
            return@requireSignature
        }
        val archive = File(regionDir, archiveName)
        if (!archive.exists() || !archive.isFile()) {
            call.respond(HttpStatusCode.NotFound)
            return@requireSignature
        }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, archiveName)
                .toString()
        )
        call.respondBytes(archive.readData(), ContentType("application", "x-sqlite3"))
    }
}
