package io.madrona.njord.geo

import mil.nga.sf.geojson.Point
import java.io.File
import kotlin.test.*

internal class S57Test {
    @Test
    fun testOpen() {
        val f = File("src/test/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        S57(f, setOf("DSID"))
    }

    @Test
    fun testProperties() {
        val f = File("src/test/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        val s57 = S57(f, setOf("DSID"))
        assertEquals(1, s57.layerGeoJson.size)
        val dsid = s57.layerGeoJson["DSID"]
        assertNotNull(dsid)

        assertEquals(dsid.features?.size, 1)
        val props = dsid.features?.first()?.properties
        assertEquals(props?.get("DSID_UPDN"), "4")
        assertEquals(props?.get("DSID_ISDT"), "20200626")
    }

    @Test
    fun testChartInfo() {
        val f = File("src/test/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())

        val s57 = S57(f)
        val info = s57.chartInsertInfo()
        assertNotNull(info)
    }

    @Test
    fun testSoundings() {
        val f = File("src/test/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        val s57 = S57(f)

        val fc = s57.findLayer("SOUNDG")

        val props = fc?.features?.firstOrNull()?.properties
        assertNotNull(props)
        val depthMeters = props["METERS"] as? Float
        assertEquals(15.8f, depthMeters)

        val feet = props["FEET"] as? Float
        assertEquals(51.83f, feet)

        val fathoms = props["FATHOMS"] as? Int
        assertEquals(8, fathoms)

        val fathomsFt = props["FATHOMS_FT"] as? Int
        assertEquals(3, fathomsFt)

        val soundg = fc.features.firstOrNull()?.geometry as? Point
        assertNotNull(soundg)
        assertFalse(soundg.coordinates.hasZ())

    }
}