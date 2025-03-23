package io.madrona.njord.geojson

import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeometryCollectionTest {

    @Test
    fun deserialize() {
        val json = """
          { "type": "GeometryCollection",
            "geometries": [
              { "type": "Point",
                "coordinates": [100.0, 0.0]
                },
              { "type": "LineString",
                "coordinates": [ [101.0, 0.0], [102.0, 1.0] ]
                }
            ]
          }
        """
        val gc = decodeFromString<GeometryCollection>(json)
        assertEquals(Point(100.0, 0.0), gc.geometries[0])
        assertEquals(
            LineString(
                listOf(
                    Position(101.0, 0.0),
                    Position(102.0, 1.0)
                )
            ), gc.geometries[1]
        )
        assertEquals(2, gc.geometries.size)
    }
}