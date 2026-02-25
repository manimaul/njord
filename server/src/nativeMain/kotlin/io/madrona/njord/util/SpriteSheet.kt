package io.madrona.njord.util

import io.madrona.njord.model.IconInfo
import io.madrona.njord.model.Sprite
import io.madrona.njord.model.ThemeMode
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import libgd.gdFree
import libgd.gdImageAlphaBlending
import libgd.gdImageCopy
import libgd.gdImageCreateFromPngPtr
import libgd.gdImageCreateTrueColor
import libgd.gdImageDestroy
import libgd.gdImagePngPtr
import libgd.gdImageSaveAlpha

class SpriteSheet {

    private fun resNameBase(theme: ThemeMode): String {
        return "www/sprites/${theme.name.lowercase()}_simplified@2x"
    }

    val spritesByTheme: Map<ThemeMode, Map<Sprite, IconInfo>> by lazy {
        ThemeMode.entries.associateWith { theme ->
            resourceAsString("${resNameBase(theme)}.json")?.let { sheet ->
                Json.decodeFromString(MapSerializer(Sprite.serializer(), IconInfo.serializer()), sheet)
            } ?: emptyMap()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private val decodedByTheme by lazy {
        ThemeMode.entries.associateWith { theme ->
            resourceBytes("${resNameBase(theme)}.png")?.let { pngBytes ->
                pngBytes.usePinned { pinned ->
                    gdImageCreateFromPngPtr(pngBytes.size, pinned.addressOf(0))
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun spriteImage(theme: ThemeMode, name: Sprite): ByteArray? {
        val info = spritesByTheme[theme]?.get(name) ?: return null
        val src = decodedByTheme[theme] ?: return null
        val dst = gdImageCreateTrueColor(info.width, info.height) ?: return null
        gdImageAlphaBlending(dst, 0)
        gdImageSaveAlpha(dst, 1)
        gdImageCopy(dst, src, 0, 0, info.x, info.y, info.width, info.height)
        return memScoped {
            val outSize = alloc<IntVar>()
            val ptr = gdImagePngPtr(dst, outSize.ptr)
            gdImageDestroy(dst)
            ptr?.let {
                val bytes = it.reinterpret<ByteVar>().readBytes(outSize.value)
                gdFree(it)
                bytes
            }
        }
    }
}
