package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.EncUpload
import io.madrona.njord.util.File
import io.madrona.njord.util.UUID
import io.madrona.njord.util.logger
import kotlinx.io.readByteArray

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

    @OptIn(InternalAPI::class)
    override suspend fun handlePost(call: ApplicationCall) = call.requireSignature {
        val multipartData = call.receiveMultipart(formFieldLimit = Long.MAX_VALUE)
        val uuid = UUID.randomUUID().toString()
        val tempDir = File(chartDir, uuid)
        val files = mutableListOf<String>()
        if (tempDir.mkdirs()) {
            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        log.info("file description = ${part.value}")
                    }

                    is PartData.FileItem -> {
                        val fileName = part.originalFileName as String
                        val channel = part.provider()
                        val fileBytes = channel.readRemaining().readByteArray()
                        files.add(fileName)
                        File(tempDir, fileName).writeBytes(fileBytes)
                    }

                    else -> {
                    }
                }
            }
            call.respond(
                EncUpload(
                    files = files,
                    uuid = uuid
                )
            )
        } else {
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}