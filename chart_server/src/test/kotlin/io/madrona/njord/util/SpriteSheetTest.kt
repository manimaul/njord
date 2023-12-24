package io.madrona.njord.util

import io.madrona.njord.Singletons
import io.madrona.njord.layers.Theme
import kotlin.test.Test
import kotlin.test.assertNotNull


class SpriteSheetTest {

    @Test
    fun spriteImage() {
        val sheet = Singletons.spriteSheet
        assertNotNull(sheet.spriteImage(Theme.Day, "ACHARE02"))
        assertNotNull(sheet.spriteImage(Theme.Dusk, "ACHARE02"))
        assertNotNull(sheet.spriteImage(Theme.Night, "ACHARE02"))
    }
}