package io.madrona.njord.endpoints

import com.willkamp.vial.api.EndPointHandler
import com.willkamp.vial.api.Request
import io.madrona.njord.ext.mimeType
import io.madrona.njord.resourceBytes
import io.netty.handler.codec.http.HttpResponseStatus
import java.net.URLDecoder

private const val content = "content"

class StaticContentHandler : EndPointHandler {

    override val route = "/v1/content/:*$content"

    override fun handle(request: Request) {
        request.pathParam(content)?.let { name ->
            val dn = URLDecoder.decode(name, "UTF-8")
            resourceBytes("/www/$dn")?.let { data ->
                name.mimeType()?.let {
                    request.respondWith { builder ->
                        builder.setBodyData(it, data)
                    }
                }
            }
        } ?: run {
            request.respondWith { builder ->
                builder.setBodyText("not found")
                        .setStatus(HttpResponseStatus.NOT_FOUND)
            }
        }
    }
}