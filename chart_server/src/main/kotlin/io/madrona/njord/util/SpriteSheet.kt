package io.madrona.njord.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.madrona.njord.IconInfo
import io.madrona.njord.Singletons
import io.madrona.njord.endpoints.IconHandler
import io.madrona.njord.ext.decodeJson
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class SpriteSheet(
    private val chartSymbolSprites: String = Singletons.config.chartSymbolSprites,
    protected val objectMapper: ObjectMapper = Singletons.objectMapper,
) {

    private val resNameBase = "/www/sprites/${chartSymbolSprites}@2x"

    private val spriteSheetImage: BufferedImage by lazy {
        IconHandler::class.java.getResourceAsStream("${resNameBase}.png").use { iss ->
            ImageIO.read(iss)
        }
    }

    private val spriteSheetJson: Map<String, IconInfo> by lazy {
        val sheet = resourceAsString("www/sprites/${chartSymbolSprites}@2x.json")
        objectMapper.readValue(sheet, object: TypeReference<Map<String, IconInfo>>() {})
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
