package io.madrona.njord.endpoints

import File
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.EncUpload
import io.madrona.njord.util.logger

class EncSaveHandler(
    val chartDir: File = Singletons.chartUploadDir,
    val chartIngestWorkDir: File = Singletons.chartIngestWorkDir
) : KtorHandler {
    override val route = "/v1/enc_save"
    private val log = logger()

    init {
        chartDir.mkdirs()
        log.info("chart save dir setup ${chartDir.getAbsolutePath()} exists=${chartDir.exists()} is_dir=${chartDir.isDirectory()}")
    }

    override suspend fun handleDelete(call: ApplicationCall) = call.requireSignature {
        chartIngestWorkDir.listFiles(false).forEach {
            it.deleteRecursively()
        }
        call.respond(HttpStatusCode.OK)
    }

    override suspend fun handlePost(call: ApplicationCall) = call.requireSignature {
        call.request.queryParameters["filename"]?.let { fileName: String ->
            val fileBytes = call.receive<ByteArray>()
            val dest = File(chartDir, fileName)
            val tmp = File(chartDir, "$fileName.tmp")
            tmp.writeBytes(fileBytes)
            tmp.renameTo(dest.getAbsolutePath().toString())
            call.respond(EncUpload(zipFiles = listOf(fileName)))
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
}
