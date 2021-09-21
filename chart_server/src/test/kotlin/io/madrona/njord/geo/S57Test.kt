package io.madrona.njord.geo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

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

        assertEquals(dsid?.features?.size, 1)
        val props = dsid?.features?.first()?.properties
        assertEquals(props?.get("DSID_UPDN"), "4")
        assertEquals(props?.get("DSID_ISDT"), "20200626")
    }
}