package io.madrona.njord.geojson

import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureCollectionTest {

    @Test
    fun deserialize() {
        val fcJson = """
            { 
              "type": "FeatureCollection",
              "features": [
                { "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [102.0, 0.5]},
                  "properties": {"prop0": "value0"}
                },
                { "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [102.0, 0.0], [103.0, 1.0], [104.0, 0.0], [105.0, 1.0]
                    ]
                  },
                  "properties": {
                    "prop0": "value0",
                    "prop1": 0.0
                  }
                },
                { "type": "Feature",
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [
                      [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
                        [100.0, 1.0], [100.0, 0.0] ]
                    ]
                  },
                  "properties": {
                    "prop0": "value0",
                    "prop1": {"this": "that"}
                  }
                }
              ]
            }
        """
        val fc = decodeFromString<FeatureCollection>(fcJson)
        assertEquals(GeometryType.Point, fc.features[0].geometry?.type)
        assertEquals("value0", fc.features[0].properties.stringValue("prop0"))
        assertEquals(1, fc.features[0].properties.size)

        assertEquals(GeometryType.LineString, fc.features[1].geometry?.type)
        assertEquals("value0", fc.features[1].properties.stringValue("prop0"))
        assertEquals(0.0f, fc.features[1].properties.floatValue("prop1")!!, 0.0f)
        assertEquals(2, fc.features[1].properties.size)

        assertEquals(GeometryType.Polygon, fc.features[2].geometry?.type)
        assertEquals("value0", fc.features[2].properties.stringValue("prop0"))
        assertEquals("that", fc.features[2].properties.objValue("prop1")!!.stringValue("this"))
        assertEquals(2, fc.features[2].properties.size)
    }

}