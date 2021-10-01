package io.madrona.njord.geo

import io.madrona.njord.db.InsertSuccess
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.s57Props
import io.madrona.njord.geo.symbols.stringValue
import io.madrona.njord.model.ChartInsert
import mil.nga.sf.geojson.Point
import java.io.File
import kotlin.test.*

internal class S57Test {
    @Test
    fun testOpen() {
        val f = File("src/test/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        S57(f)
    }

    @Test
    fun testProperties() {
        val f = File("src/test/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        val s57 = S57(f)
        assertEquals(67, s57.layerNames.size)

        val dsid = s57.findLayer("DSID")
        assertNotNull(dsid)

        val props = dsid.features?.firstOrNull()?.s57Props()
        assertNotNull(props)

        assertEquals(props.stringValue("DSID_UPDN"), "4")
        assertEquals(props.intValue("DSID_UPDN"), 4)
        assertEquals(props.stringValue("DSID_ISDT"), "20200626")
        assertEquals(props.intValue("DSID_ISDT"), 20200626)
    }

    @Test
    fun testChartInfo() {
        val f = File("src/test/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())

        val s57 = S57(f)
        val info = s57.chartInsertInfo()
        assertTrue(info is InsertSuccess<ChartInsert>)
    }

    @Test
    fun testSoundings() {
        val f = File("src/test/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        val s57 = S57(f)

        val fc = s57.findLayer("SOUNDG")

        assertTrue(s57.layerNames.isNotEmpty())

        val props = fc?.features?.firstOrNull()?.s57Props()
        assertNotNull(props)
        val depthMeters = props.floatValue("METERS")
        assertEquals(15.8f, depthMeters)

        val feet = props.floatValue("FEET")
        assertEquals(51.8f, feet)


        val fathoms = props.intValue("FATHOMS")
        assertEquals(8, fathoms)

        val fathomsFt = props.intValue("FATHOMS_FT")
        assertEquals(3, fathomsFt)

        val soundg = fc.features.firstOrNull()?.geometry as? Point
        assertNotNull(soundg)
        assertFalse(soundg.coordinates.hasZ())

        val scaMin = props.intValue("SCAMIN")
        assertEquals(17999, scaMin)

        val minZ = props.intValue("MINZ")
        assertEquals(13, minZ)
        assertNotNull(minZ)
    }

    @Test
    fun testBoySpp() {
        val f = File("src/test/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        val s57 = S57(f)

        val fc = s57.findLayer("BOYSPP")
        assertNotNull(fc)
    }

    @Test
    fun testLnDare() {
        val f = File("src/test/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        val s57 = S57(f)

        val fc = s57.findLayer("LNDARE")
        assertNotNull(fc)
    }
}