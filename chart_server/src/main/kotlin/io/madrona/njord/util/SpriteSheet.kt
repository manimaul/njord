package io.madrona.njord.util

import io.ktor.http.*
import io.ktor.response.*
import io.madrona.njord.IconInfo
import io.madrona.njord.Singletons
import io.madrona.njord.Theme
import io.madrona.njord.endpoints.IconHandler
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class SpriteSheet(
    private val chartSymbolSprites: String = Singletons.config.chartSymbolSprites
) {

    fun nameBase(theme: Theme) = chartSymbolSprites.replace("{theme}", theme.name.lowercase())
    private fun resNameBase(theme: Theme) = "/www/sprites/${nameBase(theme)}"

    private val spriteSheets: Map<Theme, BufferedImage> by lazy {
        Theme.values().associate { theme ->
            IconHandler::class.java.getResourceAsStream("${resNameBase(theme)}.png").use { iss ->
                theme to ImageIO.read(iss)
            }
        }
    }

    private fun spriteSheet(theme: Theme) : BufferedImage {
        return spriteSheets[theme] ?: throw RuntimeException("sprite sheet not found for theme $theme")
    }

    private fun spriteSheetJson(theme: Theme) : Map<String, IconInfo> {
        val resName = "www/sprites/rastersymbols-${theme.name.lowercase()}"
        return resourceAsString("$resName.json")?.let {
            Json.decodeFromString(it)
        } ?: throw RuntimeException("sprite sheet json not found for theme $theme")
    }

    fun spriteImage(theme: Theme, name: String) : ByteArray? {
        val image = spriteSheet(theme)
        return spriteSheetJson(theme)[name.replace(".png", "")]?.let { iconInfo ->
            val subImage = image.getSubimage(iconInfo.x, iconInfo.y, iconInfo.width, iconInfo.height)
            ByteArrayOutputStream(1024).use { oss ->
                ImageIO.write(subImage, "png", oss)
                oss.toByteArray()
            }
        }
    }
}
