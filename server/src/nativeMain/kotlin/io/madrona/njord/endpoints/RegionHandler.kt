package io.madrona.njord.endpoints

import File
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.madrona.njord.RegionExportConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ingest.RegionExporter
import kotlinx.coroutines.*

/**
 * GET  /v1/regions — returns every region configured in [io.madrona.njord.ChartsConfig.regionExports]
 * as a [io.madrona.njord.model.RegionManifestEntry]; regions with no rendered archive yet still
 * appear, with `archive`/`createdAt` set to null.
 * POST /v1/regions — triggers immediate (forced) generation for the supplied region config.
 */
class RegionHandler(
    private val exporter: RegionExporter = RegionExporter(),
) : KtorHandler, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    override val route = "/v1/regions"

    override suspend fun handleGet(call: ApplicationCall) {
        call.respond(exporter.buildManifest())
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
