package geos

import kotlin.test.*

class GeosTest {

    @Test
    fun testCentroid() {
        val wkt = "POLYGON ((189 115, 200 170, 130 170, 35 242, 156 215, 210 290, 274 256, 360 190, 267 215, 300 50, 200 60, 189 115))";
        Geos.createWKTReader().use {
            it.read(wkt)
        }.use { polygon ->
            val centroid = polygon.centroid()
            assertNotNull(centroid)

            Geos.createWKTWriter().use { writer ->
                val cWkt = writer.write(centroid)
                assertEquals("POINT (219.2369982354857996 174.3599424534739910)", cWkt)
            }
        }
    }

    @Test
    fun test() {
        val wkt = "POLYGON ((189 115, 200 170, 130 170, 35 242, 156 215, 210 290, 274 256, 360 190, 267 215, 300 50, 200 60, 189 115))";
        Geos.createWKTReader().use {
            it.read(wkt)
        }.use { polygon ->
            assertFalse(polygon.isEmpty)
            assertEquals(1, polygon.numGeometries)
            assertEquals(GeosGeomType.Polygon, polygon.type)
            assertEquals(GeosGeomType.Point, polygon.centroid().use { it.type })
        }
    }

    @Test
    fun testEmpty() {
        Geos.createPolygon().use { polygon ->
            assertTrue(polygon.isEmpty)
        }
    }
}