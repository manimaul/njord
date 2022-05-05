package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.IconInfo
import io.madrona.njord.Singletons
import io.madrona.njord.Theme
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.letTwo
import io.madrona.njord.util.SpriteSheet
import io.madrona.njord.util.resourceAsString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


class IconHandler(
    private val spriteSheet: SpriteSheet = Singletons.spriteSheet
) : KtorHandler {

    override val route = "/v1/icon/{theme}/{name}"

    override suspend fun handleGet(call: ApplicationCall) {
        letTwo(Theme.fromName(call.parameters["theme"]), call.parameters["name"]) { theme, name ->
            spriteSheet.spriteImage(theme, name)?.let { imageBytes ->
                call.respondBytes(imageBytes, ContentType.Image.PNG)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }
}
