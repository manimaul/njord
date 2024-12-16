package io.madrona.njord.geojson

import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals


class MultiPolygonTest {

    @Test
    fun deserialize() {
        decodeFromString<MultiPolygon>(
            """
            { "type": "MultiPolygon",
              "coordinates": [
                [
                      [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0] ],
                      [ [100.2, 0.2], [100.8, 0.2], [100.8, 0.8], [100.2, 0.8], [100.2, 0.2] ]
                ]
              ]
            }
        """.trimIndent()
        )

        decodeFromString<MultiPolygon>(
            """
            { "type": "MultiPolygon",
               "coordinates": [
                 [[[102.0, 2.0], [103.0, 2.0], [103.0, 3.0], [102.0, 3.0], [102.0, 2.0]]],
                 [[[100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0]],
                  [[100.2, 0.2], [100.8, 0.2], [100.8, 0.8], [100.2, 0.8], [100.2, 0.2]]]
                 ]
            }
        """.trimIndent()
        )
    }


    @Test
    fun serialize() {
        val geo = MultiPolygon(
            listOf(
                Polygon(
                    LineString(
                        Position(100.1, 0.1),
                        Position(100.2, 0.2),
                        Position(200.2, 200.3),
                        Position(100.1, 0.1),
                    )
                )
            )
        )
        val json = encodeToString(MultiPolygon.serializer(), geo)
        assertEquals("""{"coordinates":[[[[100.1,0.1],[100.2,0.2],[200.2,200.3],[100.1,0.1]]]],"type":"MultiPolygon"}""", json)
    }
}