package io.madrona.njord.geojson

import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals


class MultiLineStringTest {

    @Test
    fun deserialize() {
        val json = """
{
  "type": "MultiLineString",
  "coordinates": [
    [
      [ 100.0, 0.0 ], [ 101.0, 2.0 ]
    ],
    [
      [ 200.0, 0.0 ], [ 202.0, 4.0 ]
    ]
  ]
}
        """.trimIndent()
        val geo = decodeFromString<MultiLineString>(json)
        assertEquals(
            expected = MultiLineString(
                LineString(Position(100.0, 0.0), Position(101.0, 2.0)),
                LineString(Position(200.0, 0.0), Position(202.0, 4.0)),
            ),
            actual = geo
        )
    }


    @Test
    fun serialize() {
        val geo: Geometry = MultiLineString(
            LineString(Position(100.0, 0.0), Position(101.0, 2.0)),
            LineString(Position(200.0, 0.0), Position(202.0, 4.0)),
        )
        val json = encodeToString(Geometry.serializer(), geo)
        assertEquals("""{"coordinates":[[[100.0,0.0],[101.0,2.0]],[[200.0,0.0],[202.0,4.0]]],"type":"MultiLineString"}""", json)
    }
}