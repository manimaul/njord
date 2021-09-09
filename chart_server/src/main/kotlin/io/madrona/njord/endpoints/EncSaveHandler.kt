package io.madrona.njord.endpoints

import com.willkamp.vial.api.EndPointHandler
import com.willkamp.vial.api.Request
import io.madrona.njord.ChartsConfig
import io.madrona.njord.logger
import io.madrona.njord.model.EncUpload
import io.netty.handler.codec.http.HttpMethod
import java.io.File
import java.util.*

class EncSaveHandler(config: ChartsConfig) : EndPointHandler {
    override val route = "/v1/enc_save"
    private val log = logger()
    override val method: HttpMethod = HttpMethod.POST
    private val charDir = config.chartTempData

    override fun handle(request: Request) {
        log.info("enc_save size = ${request.bodyBin?.size}")
        val uuid = UUID.randomUUID().toString()
        val tempDir = File(charDir, uuid)
        tempDir.mkdirs()
        request.respondWith { builder ->
            builder.setBodyJson(EncUpload(
                    files = listOf("file1", "file2"),
                    uuid = uuid
            ))
        }
    }
}