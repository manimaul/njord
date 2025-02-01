package io.madrona.njord.geo

import io.madrona.njord.db.InsertSuccess
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.stringValue
import io.madrona.njord.geojson.Point
import io.madrona.njord.geojson.floatValue
import io.madrona.njord.geojson.intValue
import io.madrona.njord.geojson.stringValue
import io.madrona.njord.model.ChartInsert
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

internal class S57Test {
    @Test
    fun testOpen() = runBlocking {
        val f = File("src/jvmTest/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        val s57 = S57(f)
        val count = s57.featureCount()
        assertEquals(5164, count)
    }

    @Test
    fun testProperties() = runBlocking {
        val f = File("src/jvmTest/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        val s57 = S57(f)
        assertEquals(67, s57.layers.size)

        val dsid = s57.findLayer("DSID")
        assertNotNull(dsid)

        val props = dsid.features.firstOrNull()?.properties
        assertNotNull(props)

        assertEquals(props.stringValue("DSID_UPDN"), "4")
        assertEquals(props.intValue("DSID_UPDN"), 4)
        assertEquals(props.stringValue("DSID_ISDT"), "20200626")
        assertEquals(props.intValue("DSID_ISDT"), 20200626)
    }

    @Test
    fun testChartInfo() = runBlocking {
        val f = File("src/jvmTest/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())

        val s57 = S57(f)
        val info = s57.chartInsertInfo()
        assertTrue(info is InsertSuccess<ChartInsert>)
    }

    @Test
    fun testSoundings() = runBlocking {
        val f = File("src/jvmTest/data/US5WA22M/US5WA22M.000")
        assertTrue(f.exists())
        val s57 = S57(f)

        val fc = s57.findLayer("SOUNDG")

        assertTrue(s57.layers.isNotEmpty())

        val props = fc?.features?.firstOrNull()?.properties
        assertNotNull(props)
        val depthMeters = props.floatValue("METERS")
        assertEquals(15.8f, depthMeters)

        val soundg = fc.features.firstOrNull()?.geometry as? Point
        assertNotNull(soundg)
        assertNull(soundg.position.z)

        val scaMin = props.intValue("SCAMIN")
        assertEquals(17999, scaMin)

        val minZ = props.intValue("MINZ")
        assertNotNull(minZ)
        assertEquals(13, minZ)
    }

    @Test
    fun testBoySpp() {
        runBlocking {
            val f = File("src/jvmTest/data/US5WA22M/US5WA22M.000")
            assertTrue(f.exists())
            val s57 = S57(f)

            val fc = s57.findLayer("BOYSPP")
            assertNotNull(fc)
        }
    }

    @Test
    fun testLnDare() {
        runBlocking {
            val f = File("src/jvmTest/data/US5WA22M/US5WA22M.000")
            assertTrue(f.exists())
            val s57 = S57(f)

            val fc = s57.findLayer("LNDARE")
            assertNotNull(fc)
        }
    }
}
