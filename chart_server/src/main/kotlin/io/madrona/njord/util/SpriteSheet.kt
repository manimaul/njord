package io.madrona.njord.util

import io.madrona.njord.IconInfo
import io.madrona.njord.Singletons
import io.madrona.njord.endpoints.IconHandler
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class SpriteSheet(
    private val chartSymbolSprites: String = Singletons.config.chartSymbolSprites
) {

    val resNameBase = "/www/sprites/${chartSymbolSprites}"

    private val spriteSheetImage: BufferedImage by lazy {
        IconHandler::class.java.getResourceAsStream("${resNameBase}.png").use { iss ->
            ImageIO.read(iss)
        }
    }

    private val spriteSheetJson: IconInfo by lazy {
        resourceAsString("www/sprites/${chartSymbolSprites}.json")?.let {
            Json.decodeFromString(it)
        } ?: throw RuntimeException("sprite sheet json not found: $chartSymbolSprites")
    }

    fun spriteImage(name: String): ByteArray {
        val subImage = spriteSheetImage.getSubimage(
            spriteSheetJson.x,
            spriteSheetJson.y,
            spriteSheetJson.width,
            spriteSheetJson.height
        )
        return ByteArrayOutputStream(1024).use { oss ->
            ImageIO.write(subImage, "png", oss)
            oss.toByteArray()
        }
    }
}
