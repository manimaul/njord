package io.madrona.njord.util

import io.madrona.njord.Singletons
import io.madrona.njord.layers.Sprite
import io.madrona.njord.layers.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class SpriteSheetTest {

    @Test
    fun testSprites() {
        val spriteSheet = Singletons.spriteSheet.spritesByTheme
        val spriteKeys = Sprite.values().toSet()
        ThemeMode.values().forEach {
            val sprites = spriteSheet[it]
            assertNotNull(sprites)
            assertEquals(spriteKeys, sprites.keys)
        }
    }

    @Test
    fun spriteImage() {
        val sheet = Singletons.spriteSheet
        assertNotNull(sheet.spriteImage(ThemeMode.Day, Sprite.ACHARE02))
        assertNotNull(sheet.spriteImage(ThemeMode.Dusk, Sprite.ACHARE02))
        assertNotNull(sheet.spriteImage(ThemeMode.Night, Sprite.ACHARE02))
    }
}