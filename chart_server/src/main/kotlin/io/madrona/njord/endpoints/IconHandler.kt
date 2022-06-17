package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.util.SpriteSheet


class IconHandler(
    private val spriteSheet: SpriteSheet = Singletons.spriteSheet
) : KtorHandler {

    override val route = "/v1/icon/{name}"

    override suspend fun handleGet(call: ApplicationCall) {
        call.parameters["name"]?.let { name ->
            spriteSheet.spriteImage(name)?.let { imageBytes ->
                call.respondBytes(imageBytes, ContentType.Image.PNG)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }
}
