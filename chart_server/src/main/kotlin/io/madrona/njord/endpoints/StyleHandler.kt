package io.madrona.njord.endpoints

import com.willkamp.vial.api.EndPointHandler
import com.willkamp.vial.api.Request
import io.madrona.njord.ext.letFromStrings
import io.madrona.njord.model.Depth
import io.madrona.njord.model.StyleColor
import io.netty.handler.codec.http.HttpResponseStatus

private const val color = "color"
private const val depth = "depth"

class StyleHandler : EndPointHandler {
    override val route = "/v1/style/:$color/:$depth"

    override fun handle(request: Request) {
        letFromStrings(request.pathParam(color), request.pathParam(depth)) { color: StyleColor, depth: Depth ->
            request.respondWith {
                it.setBodyText("todo: $color - $depth")
            }
        } ?: request.respondWith {
            it.setStatus(HttpResponseStatus.NOT_FOUND)
                    .setBodyText("not found")
        }
    }
}