package io.madrona.njord.endpoints

import File
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.EncUpload
import io.madrona.njord.util.UUID
import io.madrona.njord.util.logger

class EncSaveHandler(
    config: ChartsConfig = Singletons.config,
) : KtorHandler {
    override val route = "/v1/enc_save"
    private val log = logger()
    private val chartDir = config.chartTempData

    override suspend fun handleGet(call: ApplicationCall) = call.requireSignature {
        call.respond(
            File(chartDir).listFiles(true).map { each ->
                val files = each.listFiles(false).map { it.name }.filter { it.endsWith(".zip") }.toList()
                EncUpload(
                    files = files,
                    uuid = each.name
                )
            }
        )
    }

    override suspend fun handleDelete(call: ApplicationCall) = call.requireSignature {
        call.request.queryParameters["uuid"]?.let {
            File(chartDir, it).takeIf { it.exists() }?.deleteRecursively()
            call.respond(HttpStatusCode.OK)
        } ?: call.respond(HttpStatusCode.NotFound)
    }

    override suspend fun handlePost(call: ApplicationCall) = call.requireSignature {
        call.request.queryParameters["filename"]?.let { fileName ->
            val uuid = UUID.randomUUID().toString()
            val tempDir = File(chartDir, uuid)
            if (tempDir.mkdirs()) {
                val fileBytes = call.receive<ByteArray>()
                File(tempDir, fileName).writeBytes(fileBytes)
                call.respond(EncUpload(files = listOf(fileName), uuid = uuid))
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
}
