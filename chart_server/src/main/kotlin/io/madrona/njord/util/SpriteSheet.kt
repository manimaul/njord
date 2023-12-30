package io.madrona.njord.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.madrona.njord.IconInfo
import io.madrona.njord.Singletons
import io.madrona.njord.layers.Sprite
import io.madrona.njord.layers.Theme
import io.madrona.njord.layers.ThemeMode
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class SpriteSheet(
    private val objectMapper: ObjectMapper = Singletons.objectMapper,
) {

    private fun resNameBase(theme: ThemeMode): String {
        return "www/sprites/${theme.name.lowercase()}_simplified@2x"
    }

    private fun spriteSheetImage(theme: ThemeMode): BufferedImage {
        val name = "${resNameBase(theme)}.png"
        return javaClass.classLoader.getResourceAsStream(name).use { iss ->
            ImageIO.read(iss)
        }
    }

    val spritesByTheme: Map<ThemeMode, Map<Sprite, IconInfo>> by lazy {
        ThemeMode.values().associateWith { theme ->
            val sheet = resourceAsString("${resNameBase(theme)}.json")
            objectMapper.readValue(sheet, object : TypeReference<Map<Sprite, IconInfo>>() {})
        }
    }

    fun spriteImage(theme: ThemeMode, name: Sprite): ByteArray? {
        return spritesByTheme[theme]?.let { it[name] }?.let {
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
