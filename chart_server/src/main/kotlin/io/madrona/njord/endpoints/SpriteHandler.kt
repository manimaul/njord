package io.madrona.njord.endpoints

import com.willkamp.vial.api.EndPointHandler
import com.willkamp.vial.api.Request
import io.madrona.njord.resourceBytes
import io.netty.handler.codec.http.HttpResponseStatus

private const val sprite = "sprite"

class SpriteHandler : EndPointHandler {

    override val route = "/v1/sprite/:$sprite"

    override fun handle(request: Request) {
        request.pathParam(sprite)?.let { spriteName ->
            resourceBytes("/sprites/$spriteName")?.let {
                if (spriteName.endsWith(".json")) {
                    request.respondWith { builder ->
                        builder.setBodyData("application/json", it)
                    }
                } else if (spriteName.endsWith(".png")) {
                    request.respondWith { builder ->
                        builder.setBodyData("image/png", it)
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


}