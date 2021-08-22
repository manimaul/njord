package io.madrona.njord.endpoints

import com.willkamp.vial.api.EndPointHandler
import com.willkamp.vial.api.Request
import com.willkamp.vial.implementation.logger
import io.madrona.njord.ext.letFromStrings
import io.madrona.njord.model.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpResponseStatus
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer

private const val fontStack = "fontstack"
private const val range = "range"

class FontHandler : EndPointHandler {
    override val route = "/v1/fonts/:$fontStack/:$range"
    val log = logger()

    override fun handle(request: Request) {
        request.pathParam(fontStack)?.let { fs ->
            request.pathParam(range)?.let { rng ->
                fontResource("/fonts/$fs/$rng")?.let {
                    request.respondWith { builder ->
                        builder.setBodyData("application/x-protobuf", it)
                    }
                }
            }
        } ?: run {
            request.respondWith { builder ->
                builder.setBodyText("invalid request")
                        .setStatus(HttpResponseStatus.BAD_REQUEST)
            }
        }
    }

    private fun fontResource(name: String) : ByteArray? {
        return javaClass.getResourceAsStream(name)?.use { ips ->
            val byteList = mutableListOf<Byte>()
            val buffer = ByteArray(4096)
            var read = ips.read(buffer)
            while (read > -1) {
                for(i in 0 until read) {
                    byteList.add(buffer[i])
                }
                read = ips.read(buffer)
            }
            byteList.toByteArray()
        }
    }
}