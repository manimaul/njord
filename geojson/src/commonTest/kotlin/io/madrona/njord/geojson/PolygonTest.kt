package io.madrona.njord.geojson

import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals


class PolygonTest {

    @Test
    fun deserializeWithoutHoles() {
        val without_holes = """
  { 
    "type": "Polygon",
    "coordinates": [ [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0] ] ]
   }
   """
        val polygon = decodeFromString<Polygon>(without_holes)
        assertEquals(100.0, polygon.coordinates[0][0].longitude, 0.0)
        assertEquals(GeometryType.Polygon, polygon.type)
    }

    @Test
    fun deserializeWithHoles() {
        val with_holes = """
     { 
       "type": "Polygon",
        "coordinates": [
          [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0] ],
          [ [100.2, 0.2], [100.8, 0.2], [100.8, 0.8], [100.2, 0.8], [100.2, 0.2] ]
      ]
   }
   """
        val polygon = decodeFromString<Polygon>(with_holes)
        assertEquals(100.0, polygon.coordinates[0][0].longitude, 0.0)
        assertEquals(GeometryType.Polygon, polygon.type)
    }

    @Test
    fun serialize() {
        val geo = Polygon(
            listOf(
                listOf(
                    Position(100.1, 0.1),
                    Position(100.1, 100.1),
                    Position(0.1, 100.1),
                    Position(100.1, 0.1),
                )
            )
        )
        val json = encodeToString(Polygon.serializer(), geo)
        assertEquals("""{"coordinates":[[[100.1,0.1],[100.1,100.1],[0.1,100.1],[100.1,0.1]]],"type":"Polygon"}""", json)
    }
}