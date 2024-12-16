package io.madrona.njord.util

import io.madrona.njord.model.IconInfo
import io.madrona.njord.model.Sprite
import io.madrona.njord.model.ThemeMode
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class SpriteSheet {

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
        ThemeMode.entries.associateWith { theme ->
            resourceAsString("${resNameBase(theme)}.json")?.let { sheet ->
                Json.decodeFromString(MapSerializer(Sprite.serializer(), IconInfo.serializer()), sheet)
            } ?: emptyMap()
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
