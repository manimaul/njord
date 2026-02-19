@file:OptIn(ExperimentalForeignApi::class, NativeRuntimeApi::class)

import io.madrona.njord.geojson.FeatureCollection
import io.madrona.njord.geojson.Point
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import libgdal.GDALClose
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OgrFeatureTest {

    val testData = File("./build/tmp/test_data")

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
        testData.deleteRecursively()
        testData.mkdirs()
    }

    @Test
    fun testFeature() {
        val path = "${testData.getAbsolutePath()}/myds.json"
        val ds = GdalDataset.create(
            driverName = "GeoJSON",
            path = path,
            epsg = 4326
        )
        assertNotNull(ds)
        val layer = ds.getOrCreateLayer("LNDARE")
        assertEquals(setOf("LNDARE"), ds.layerNames)
        val wkt = "POINT (-122 48)"
        val props = mapOf<String, JsonElement>(
            "str" to JsonPrimitive("hello"),
            "myint" to JsonPrimitive(1),
            "long" to JsonPrimitive(Long.MAX_VALUE),
            "bool" to JsonPrimitive(true),
            "double" to JsonPrimitive(9.9),
            "float" to JsonPrimitive(9.8f),
            "int list" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2)) ),
            "str list" to JsonArray(listOf(JsonPrimitive("foo"), JsonPrimitive("bar")) )
        )
        OgrGeometry.fromWkt4326(wkt)?.let {
            layer.addFeature(it, props)
        }
        GDALClose(ds.ptr)

        //FeatureCollection does not have a name key but gdal provides one
        val json = Json { ignoreUnknownKeys = true }
        val fc = json.decodeFromString<FeatureCollection>(File(path).readContents())
        assertEquals(1, fc.features.size)

        val feature = fc.features.first()

        assertTrue(feature.geometry is Point)
        val point = feature.geometry as Point
        assertEquals(48.0, point.position.latitude)
        assertEquals(-122.0, point.position.longitude)
        val JsonObject = json.parseToJsonElement("""{
          "str": "hello",
          "myint": 1,
          "long": 9223372036854775807,
          "bool": true,
          "double": 9.9,
          "float": 9.8,
          "int list": [1, 2],
          "str list": ["foo", "bar"]
        }""".trimMargin())
        assertEquals(JsonObject, feature.properties)
    }
}