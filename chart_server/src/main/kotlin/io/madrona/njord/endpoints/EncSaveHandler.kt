package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.logger
import io.madrona.njord.model.EncUpload
import java.io.File
import java.util.*

class EncSaveHandler(
    config: ChartsConfig = Singletons.config
) : KtorHandler {
    override val route = "/v1/enc_save"
    private val log = logger()
    private val charDir = config.chartTempData

    override suspend fun handlePost(call: ApplicationCall) {
        val multipartData = call.receiveMultipart()
        val uuid = UUID.randomUUID().toString()
        val tempDir = File(charDir, uuid)
        val files = mutableListOf<String>()
        if (tempDir.mkdirs()) {
            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        log.info("file description = ${part.value}")
                    }
                    is PartData.FileItem -> {
                        val fileName = part.originalFileName as String
                        val fileBytes = part.streamProvider().readBytes()
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