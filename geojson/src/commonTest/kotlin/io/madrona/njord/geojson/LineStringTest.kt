package io.madrona.njord.geojson

import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals


class LineStringTest {

    @Test
    fun deserialize() {
        val json = """
          { "type": "LineString",
            "coordinates": [ [100.0, 0.0], [101.0, 1.0] ]
          }
        """.trimIndent()
        val geo = decodeFromString<LineString>(json)
        assertEquals(LineString(Position(100.0, 0.0), Position(101.0, 1.0)), geo)
    }


    @Test
    fun serialize() {
        val geo = LineString(Position(100.1, 0.1), Position(101.2, 1.2))
        val json = encodeToString(LineString.serializer(), geo)
        assertEquals("""{"coordinates":[[100.1,0.1],[101.2,1.2]],"type":"LineString"}""", json)
    }
}