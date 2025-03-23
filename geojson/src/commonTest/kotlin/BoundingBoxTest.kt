package io.madrona.njord.geojson

import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class BoundingBoxTest {

    @Test
    fun deserialize() {
        val boxJson = """[-10.0, -10.0, 10.0, 10.0]"""
        val bbox = decodeFromString<BoundingBox>(boxJson)
        assertEquals(bbox.west, -10.0)
    }

    @Test
    fun serialize() {
        val box = BoundingBox(1.1, 2.2, 3.3, 4.4)
        val json = encodeToString(BoundingBox.serializer(), box)
        assertEquals("""[1.1,2.2,3.3,4.4]""", json)
    }
}