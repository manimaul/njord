package io.madrona.njord.geo

import org.locationtech.jts.io.WKTReader
import kotlin.test.*

internal class TileSystemTest {

    @Test
    fun testTransform() {
        val subject = TileSystem()
        val z = 13
        val x = 1309
        val y = 2871
        val wgsGeom = subject.createTileClipPolygon(x, y, z)
        assertEquals(
            WKTReader().read("POLYGON ((-122.4755859375 47.3090342477478, -122.431640625 47.3090342477478, -122.431640625 47.27922900257082, -122.4755859375 47.27922900257082, -122.4755859375 47.3090342477478))"),
            wgsGeom
        )

        val tileGeom = subject.tileGeometry(wgsGeom, x, y, z)
        assertEquals(
            WKTReader().read("POLYGON ((0 4096, 4096 4096, 4096 0, 0 0, 0 4096))"),
            tileGeom
        )
    }
}