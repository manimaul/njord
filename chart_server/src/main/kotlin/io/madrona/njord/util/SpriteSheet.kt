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

    private val resNameBase = "/www/sprites/${chartSymbolSprites}@2x"

    private val spriteSheetImage: BufferedImage by lazy {
        IconHandler::class.java.getResourceAsStream("${resNameBase}.png").use { iss ->
            ImageIO.read(iss)
        }
    }

    private val spriteSheetJson: Map<String, IconInfo> by lazy {
        resourceAsString("www/sprites/${chartSymbolSprites}@2x.json")?.let {
            Json.decodeFromString(it)
        } ?: throw RuntimeException("sprite sheet json not found: $chartSymbolSprites")
    }

    fun spriteImage(name: String): ByteArray? {
        return spriteSheetJson[name]?.let {
            val subImage = spriteSheetImage.getSubimage(
                it.x,
                it.y,
                it.width,
                it.height
            )
            ByteArrayOutputStream(1024).use { oss ->
                ImageIO.write(subImage, "png", oss)
                oss.toByteArray()
            }
        }
    }
}
