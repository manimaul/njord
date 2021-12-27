package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.madrona.njord.IconInfo
import io.madrona.njord.Theme
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.letTwo
import io.madrona.njord.util.resourceAsString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO



class IconHandler : KtorHandler {

    override val route = "/v1/icon/{theme}/{name}"

    override suspend fun handleGet(call: ApplicationCall) {
        letTwo(Theme.fromName(call.parameters["theme"]), call.parameters["name"]) { theme, name ->
            val image = spriteSheets[theme]
            val resName = "www/sprites/rastersymbols-${theme.name.lowercase()}"
             resourceAsString("$resName.json")?.let {
                Json.decodeFromString<Map<String, IconInfo>>(it)
            }?.get(name.replace(".png", ""))?.let {  iconInfo ->
                 val subImage = image?.getSubimage(iconInfo.x, iconInfo.y, iconInfo.width, iconInfo.height)
                 val oss = ByteArrayOutputStream(1024)
                 ImageIO.write(subImage, "png", oss)
                 call.respondBytes(oss.toByteArray(), ContentType.Image.PNG)
             }
        } ?: call.respond(HttpStatusCode.NotFound)
    }

    companion object {
        val spriteSheets by lazy {
            Theme.values().associate { theme ->
                val resName = "/www/sprites/rastersymbols-${theme.name.lowercase()}"
                IconHandler::class.java.getResourceAsStream("$resName.png").use { iss ->
                    theme to ImageIO.read(iss)
                }
            }
        }
    }
}