package io.madrona.njord.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.madrona.njord.IconInfo
import io.madrona.njord.Singletons
import io.madrona.njord.endpoints.IconHandler
import io.madrona.njord.layers.Theme
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class SpriteSheet(
    private val objectMapper: ObjectMapper = Singletons.objectMapper,
) {

    private fun resNameBase(theme: Theme): String {
        return "www/sprites/${theme.name.lowercase()}_simplified@2x"
    }

    private fun spriteSheetImage(theme: Theme): BufferedImage {
        val name = "${resNameBase(theme)}.png"
        return javaClass.classLoader.getResourceAsStream(name).use { iss ->
            ImageIO.read(iss)
        }
    }

    private val spriteSheetJson: Map<Theme, Map<String, IconInfo>> by lazy {
        Theme.values().associateWith { theme ->
            val sheet = resourceAsString("${resNameBase(theme)}.json")
            objectMapper.readValue(sheet, object : TypeReference<Map<String, IconInfo>>() {})
        }
    }

    fun spriteImage(theme: Theme, name: String): ByteArray? {
        return spriteSheetJson[theme]?.let { it[name] }?.let {
            val subImage = spriteSheetImage(theme).getSubimage(
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
