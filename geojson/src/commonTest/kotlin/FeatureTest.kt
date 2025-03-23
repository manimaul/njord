package io.madrona.njord.geojson

import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureTest {

    @Test
    fun serialize() {
        val featureJson = """
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
            }
        """.trimIndent()

        val fc = decodeFromString<Feature>(featureJson)
        assertEquals(GeometryType.LineString, fc.geometry?.type)
        assertEquals("value0", fc.properties.stringValue("prop0"))
        assertEquals(0.0, fc.properties.doubleValue("prop1"))
        assertEquals(2, fc.properties.size)
    }
}
