package io.madrona.njord.endpoints

import File
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ingest.RegionExporter

/**
 * GET /v1/regions — returns the manifest.json listing available region archives.
 */
class RegionHandler(
    private val regionDir: File = Singletons.regionDir,
) : KtorHandler {
    override val route = "/v1/regions"

    override suspend fun handleGet(call: ApplicationCall) {
        val manifest = File(regionDir, RegionExporter.MANIFEST_FILE)
        if (!manifest.exists() || !manifest.isFile()) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        call.respondBytes(manifest.readData(), ContentType.Application.Json)
    }
}

/**
 * GET /v1/regions/{archive} — streams a region SQLite archive for download.
 */
class RegionArchiveHandler(
    private val regionDir: File = Singletons.regionDir,
) : KtorHandler {
    override val route = "/v1/regions/{archive}"

    override suspend fun handleGet(call: ApplicationCall) {
        val archiveName = call.parameters["archive"] ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        // Prevent path traversal
        if (archiveName.contains('/') || archiveName.contains("..")) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val archive = File(regionDir, archiveName)
        if (!archive.exists() || !archive.isFile()) {
            call.respond(HttpStatusCode.NotFound)
            return
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
