package io.madrona.njord.geojson

import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals


class PointTest {

    @Test
    fun deserialize() {
        val json = """
              { "type": "Point", "coordinates": [100.0, 10.0] }
        """.trimIndent()
        val point = decodeFromString<Point>(json)
        assertEquals(100.0, point.coordinates[0], 0.0)
        assertEquals(10.0, point.coordinates[1], 0.0)
        assertEquals(GeometryType.Point, point.type)
    }

    @Test
    fun deserialize2() {
        val json = """
              {"type": "Point", "coordinates": [102.0, 0.5]}
        """.trimIndent()
        val point = decodeFromString<Point>(json)
        assertEquals(102.0, point.coordinates[0], 0.0)
        assertEquals(0.5, point.coordinates[1], 0.0)
        assertEquals(GeometryType.Point, point.type)
    }

    @Test
    fun serialize() {
        val point = Point(1.1, 2.2, 3.3, 4.4)
        val json = encodeToString(Point.serializer(), point)
        assertEquals("""{"coordinates":[1.1,2.2,3.3,4.4],"type":"Point"}""", json)
    }
}