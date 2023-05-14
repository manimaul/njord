package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.util.SpriteSheet


class IconHandler(
    private val spriteSheet: SpriteSheet = Singletons.spriteSheet
) : KtorHandler {

    override val route = "/v1/icon/{name}"

    override suspend fun handleGet(call: ApplicationCall) {
        call.parameters["name"]?.let { name ->
            spriteSheet.spriteImage(name.stripExt())?.let { imageBytes ->
                call.respondBytes(imageBytes, ContentType.Image.PNG)
            }
        } ?: call.respond(HttpStatusCode.NotFound)
    }
}

private fun String.stripExt(): String {
    return lastIndexOf('.').takeIf { it > 0 }?.let {
        substring(0, it)
    } ?: this
}
