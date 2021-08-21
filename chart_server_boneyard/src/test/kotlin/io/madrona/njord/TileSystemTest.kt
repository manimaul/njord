package io.madrona.njord

import io.madrona.njord.gis.tilesystem.TileSystem
import kotlin.test.Test
import kotlin.test.assertEquals


internal class TileSystemTest {

    @Test
    fun testMapSize() {
        val ts = TileSystem()
        assertEquals(1, ts.mapSizeTiles(0))
        assertEquals(2, ts.mapSizeTiles(1))
        assertEquals(4, ts.mapSizeTiles(2))
        assertEquals(8, ts.mapSizeTiles(3))
        assertEquals(16, ts.mapSizeTiles(4))
    }
}