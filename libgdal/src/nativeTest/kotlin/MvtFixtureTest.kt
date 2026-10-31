import io.madrona.njord.geojson.Position
import io.madrona.njord.testdata.PortDivergence
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import tile.VectorTileEncoder
import tile.decodeMvtToJson
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P-TE-2 `geo/mvt/<case>.bin` + `.json`: the bytes `VectorTileEncoder.encode()` produces for
 * hand-built geometries already in tile pixel space (extent 4096, 8 px clip buffer, 0.1 px
 * simplification, 1 px minimum length / 1 px² minimum area). Kotlin verifies the bytes exactly; the
 * Rust port compares the decoded sidecar, because tag/value ordering is not part of the MVT
 * contract (RUST_PORT_PLAN.md §5.2).
 *
 * Feature ids are the encoder's own autoincrement, which starts at 2 (`++autoincrement` from 1),
 * so the first feature of every case carries id 2 - a quirk the port must copy because MapLibre
 * uses the id where the `promoteId: LNAM` source setting finds no LNAM.
 */
class MvtFixtureTest {

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
    }

    private fun p(x: Double, y: Double) = Position(x, y)
    private fun props(vararg pairs: Pair<String, JsonElement>) = mapOf(*pairs)
    private fun str(s: String) = JsonPrimitive(s)
    private fun num(n: Number) = JsonPrimitive(n)

    private fun case(
        name: String,
        pins: String,
        source: String,
        decisions: List<String> = listOf("TE-8"),
        port: ((JsonElement) -> PortDivergence<JsonElement>)? = null,
        build: VectorTileEncoder.() -> Unit,
    ): JsonElement {
        val encoder = VectorTileEncoder().apply(build)
        val bytes = encoder.encode()
        val decoded = decodeMvtToJson(bytes)
        val header = gdalHeader(pins, decisions, "MvtFixtureTest", source)
        fixtures.binary("geo/mvt/$name.bin", header, bytes, decoded, port?.invoke(decoded))
        return decoded
    }

    private fun JsonElement.layers() = jsonObject["layers"]!!.jsonArray
    private fun JsonElement.features(layer: Int = 0) = layers()[layer].jsonObject["features"]!!.jsonArray
    private fun JsonElement.content() = (this as JsonPrimitive).content
    private fun JsonElement.geometry() = jsonObject["geometry"].toString()
    private fun JsonElement.prop(key: String) = jsonObject["props"]!!.jsonObject[key]!!.content()

    @Test
    fun `empty encoder`() {
        val decoded = case("empty", "an encoder with no features encodes to an empty tile", "no input") {}
        assertEquals(0, decoded.layers().size)
    }

    @Test
    fun `point with a property of every JSON type`() {
        val decoded = case(
            "point_props",
            "property value mapping: strings (incl. empty and \"0\") stay strings, ints -> int_value, longs beyond Int -> sint_value, doubles -> double_value, booleans -> bool_value, arrays -> their JSON text as a string",
            "one BOYLAT point at (100, 200)",
        ) {
            addFeature(
                "BOYLAT", props(
                    "str" to str("red can"),
                    "empty" to str(""),
                    "zero" to str("0"),
                    "int" to num(7),
                    "negative" to num(-42),
                    "long" to num(9_007_199_254_740_993L),
                    "double" to num(12.3),
                    "negativeDouble" to num(-0.6),
                    "bool" to JsonPrimitive(true),
                    "list" to JsonArray(listOf(str("2"), str("4"))),
                    "LNAM" to str("0226F7A5C0ED2C4E"),
                ), Gdal.createPoint(p(100.0, 200.0))
            )
        }
        val feature = decoded.features().single()
        assertEquals("POINT", feature.jsonObject["type"]!!.content())
        assertEquals("[\"2\",\"4\"]", feature.prop("list"))
        assertEquals("""[[[100,200]]]""", feature.geometry())
    }

    @Test
    fun `lines keep delta encoding and drop repeated vertices`() {
        val decoded = case(
            "lines",
            "LineString and MultiLineString command streams: zig-zag deltas, consecutive duplicate vertices collapsed, a multi-part line whose parts are separate MoveTo runs",
            "diagonal across the tile, a line with repeated points, a two-part multilinestring",
        ) {
            addFeature("DEPCNT", props("VALDCO" to num(10.0)), Gdal.createLineString(p(0.0, 0.0), p(4096.0, 4096.0)))
            addFeature("DEPCNT", props("VALDCO" to num(2.0)), Gdal.createLineString(p(10.0, 10.0), p(10.0, 10.0), p(20.0, 10.0), p(20.0, 10.0), p(20.0, 30.0)))
            addFeature(
                "COALNE", emptyMap(), Gdal.createMultiLineString(
                    Gdal.createLineString(p(100.0, 100.0), p(200.0, 100.0)),
                    Gdal.createLineString(p(300.0, 300.0), p(300.0, 400.0), p(350.0, 450.0)),
                )
            )
        }
        assertEquals(2, decoded.layers().size)
        assertEquals("""[[[10,10],[20,10],[20,30]]]""", decoded.features()[1].geometry())
    }

    @Test
    fun `polygon winding is normalised and holes are kept`() {
        val decoded = case(
            "polygons",
            "exterior rings are emitted clockwise on screen and interior rings counter-clockwise regardless of input winding; holes stay with their shell; a MultiPolygon is one feature with several ring groups",
            "a CCW square, a CW square, a square with a hole given CW, a multipolygon of two parts one with a hole",
        ) {
            // counter-clockwise in pixel space (y down)
            addFeature("DEPARE", props("DRVAL1" to num(0.0), "DRVAL2" to num(5.0)), Gdal.createPolygon(p(100.0, 100.0), p(100.0, 500.0), p(500.0, 500.0), p(500.0, 100.0), p(100.0, 100.0)))
            // clockwise in pixel space
            addFeature("DEPARE", props("DRVAL1" to num(5.0), "DRVAL2" to num(10.0)), Gdal.createPolygon(p(600.0, 100.0), p(1000.0, 100.0), p(1000.0, 500.0), p(600.0, 500.0), p(600.0, 100.0)))
            addFeature(
                "LNDARE", props("OBJNAM" to str("Island")), Gdal.createPolygon(
                    Gdal.createLinearRing(p(1200.0, 1200.0), p(2000.0, 1200.0), p(2000.0, 2000.0), p(1200.0, 2000.0), p(1200.0, 1200.0)),
                    Gdal.createLinearRing(p(1400.0, 1400.0), p(1800.0, 1400.0), p(1800.0, 1800.0), p(1400.0, 1800.0), p(1400.0, 1400.0)),
                )
            )
            addFeature(
                "LNDARE", emptyMap(), Gdal.createMultiPolygon(
                    Gdal.createPolygon(p(2500.0, 2500.0), p(2500.0, 2600.0), p(2600.0, 2600.0), p(2600.0, 2500.0), p(2500.0, 2500.0)),
                    Gdal.createPolygon(
                        Gdal.createLinearRing(p(3000.0, 3000.0), p(3500.0, 3000.0), p(3500.0, 3500.0), p(3000.0, 3500.0), p(3000.0, 3000.0)),
                        Gdal.createLinearRing(p(3100.0, 3100.0), p(3100.0, 3200.0), p(3200.0, 3200.0), p(3200.0, 3100.0), p(3100.0, 3100.0)),
                    ),
                )
            )
        }
        val depare = decoded.features(0)
        // both squares come out with the same orientation
        assertEquals("""[[[100,100],[500,100],[500,500],[100,500],[100,100]]]""", depare[0].geometry())
        assertEquals("""[[[600,100],[1000,100],[1000,500],[600,500],[600,100]]]""", depare[1].geometry())
        val island = decoded.features(1)[0].jsonObject["geometry"]!!.jsonArray
        assertEquals(2, island.size, "shell plus one hole")
    }

    @Test
    fun `multipoint and geometry collection`() {
        val decoded = case(
            "multi",
            "a GeometryCollection is split into one feature per member sharing the same id. A MultiPoint must be one POINT feature with one MoveTo of count n: Kotlin drops it (D13, `OGR_G_GetPoints` sees no points on a MultiPoint) and leaves its layer empty; port.decoded has the feature",
            "three soundings as a multipoint, a collection of a point and a line",
            decisions = listOf("TE-8", "D13"),
            port = { decoded -> PortDivergence("D13", withSoundgMultiPoint(decoded)) },
        ) {
            addFeature("SOUNDG", props("METERS" to num(1.2)), Gdal.createMultiPointFromCoords(p(10.0, 10.0), p(20.0, 20.0), p(30.0, 10.0)))
            val collection = Gdal.createGeometryCollection()
            collection.addGeometry(Gdal.createPoint(p(500.0, 500.0)))
            collection.addGeometry(Gdal.createLineString(p(500.0, 500.0), p(600.0, 700.0)))
            addFeature("MIXED", props("K" to str("v")), collection)
        }
        val mixed = decoded.features(1)
        assertEquals(2, mixed.size)
        assertEquals(mixed[0].jsonObject["id"], mixed[1].jsonObject["id"], "collection members keep one id")
    }

    /**
     * The `multi` view the port must produce (D13): the SOUNDG layer holds the MultiPoint as one
     * POINT feature with the first id (2), its three points as one MoveTo; MIXED is unchanged.
     */
    private fun withSoundgMultiPoint(decoded: JsonElement): JsonElement {
        fun pt(x: Int, y: Int) = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y)))))
        val soundg = buildJsonObject {
            put("id", 2)
            put("type", "POINT")
            put("props", buildJsonObject { put("METERS", 1.2) })
            put("geometry", JsonArray(listOf(pt(10, 10), pt(20, 20), pt(30, 10))))
        }
        val layers = decoded.layers().map { layer ->
            val l = layer.jsonObject
            if (l["name"]!!.content() == "SOUNDG") JsonObject(l + ("features" to JsonArray(listOf(soundg)))) else l
        }
        return JsonObject(decoded.jsonObject + ("layers" to JsonArray(layers)))
    }

    @Test
    fun `clipping at the 8 pixel buffer`() {
        val decoded = case(
            "clipping",
            "geometries are clipped to the tile extent plus an 8px buffer: a point inside the buffer is kept and one outside dropped, a polygon/line crossing the edge is cut at the buffer, a geometry wholly outside is dropped",
            "points at x=4100 (kept) and 4105 (dropped), a polygon reaching 5000, a line to 6000, a square beyond the tile",
        ) {
            addFeature("BOYLAT", props("N" to num(1)), Gdal.createPoint(p(4100.0, 100.0)))
            addFeature("BOYLAT", props("N" to num(2)), Gdal.createPoint(p(4105.0, 100.0)))
            addFeature("BOYLAT", props("N" to num(3)), Gdal.createPoint(p(-8.0, -8.0)))
            addFeature("BOYLAT", props("N" to num(4)), Gdal.createPoint(p(-10.0, 0.0)))
            addFeature("DEPARE", props("N" to num(5)), Gdal.createPolygon(p(3000.0, 3000.0), p(5000.0, 3000.0), p(5000.0, 5000.0), p(3000.0, 5000.0), p(3000.0, 3000.0)))
            addFeature("DEPCNT", props("N" to num(6)), Gdal.createLineString(p(2000.0, 2000.0), p(6000.0, 2000.0)))
            addFeature("DEPARE", props("N" to num(7)), Gdal.createPolygon(p(5000.0, 5000.0), p(6000.0, 5000.0), p(6000.0, 6000.0), p(5000.0, 6000.0), p(5000.0, 5000.0)))
        }
        val points = decoded.features(0)
        assertEquals(listOf(1, 3), points.map { it.prop("N").toInt() })
        val depare = decoded.features(1)
        assertEquals(1, depare.size, "the wholly-outside polygon is dropped")
        // GEOS picks the ring's start vertex; the vertex set is what matters
        assertEquals(
            setOf("[3000,3000]", "[4104,3000]", "[4104,4104]", "[3000,4104]"),
            depare[0].jsonObject["geometry"]!!.jsonArray.single().jsonArray.map { it.toString() }.toSet(),
        )
        assertEquals("""[[[2000,2000],[4104,2000]]]""", decoded.features(2)[0].geometry())
    }

    @Test
    fun `tiny geometries are dropped and near-collinear vertices simplified`() {
        val decoded = case(
            "tiny_and_simplified",
            "polygons under 1px² and lines under 1px are dropped before encoding; vertices within 0.1px of the line through their neighbours are simplified away; sub-pixel coordinates round to the nearest integer",
            "a 0.5px square, a 0.5px line, a 1.5px line, a line with a 0.05px kink, a polygon with 0.3px jitter",
        ) {
            addFeature("TINY", props("N" to num(1)), Gdal.createPolygon(p(10.0, 10.0), p(10.5, 10.0), p(10.5, 10.5), p(10.0, 10.5), p(10.0, 10.0)))
            addFeature("TINY", props("N" to num(2)), Gdal.createLineString(p(10.0, 10.0), p(10.5, 10.0)))
            addFeature("TINY", props("N" to num(3)), Gdal.createLineString(p(10.0, 10.0), p(11.5, 10.0)))
            addFeature("TINY", props("N" to num(4)), Gdal.createLineString(p(100.0, 100.0), p(150.0, 100.05), p(200.0, 100.0)))
            addFeature("TINY", props("N" to num(5)), Gdal.createPolygon(p(300.0, 300.0), p(300.3, 400.0), p(400.0, 400.3), p(400.0, 300.0), p(300.0, 300.0)))
        }
        val features = decoded.features()
        assertEquals(listOf(3, 4, 5), features.map { it.prop("N").toInt() })
        assertEquals("""[[[100,100],[200,100]]]""", features[1].geometry(), "the 0.05px kink is simplified away")
    }

    @Test
    fun `keys and values are deduplicated per layer`() {
        val decoded = case(
            "shared_keys_values",
            "a layer's key and value tables are deduplicated in first-seen order and each feature's tags index into them; equal values of different JSON types stay distinct entries",
            "four BOYLAT points sharing CATLAM/COLOUR values, one with the string \"1\" vs the int 1",
        ) {
            addFeature("BOYLAT", props("CATLAM" to num(1), "COLOUR" to str("3")), Gdal.createPoint(p(100.0, 100.0)))
            addFeature("BOYLAT", props("CATLAM" to num(2), "COLOUR" to str("4")), Gdal.createPoint(p(200.0, 100.0)))
            addFeature("BOYLAT", props("CATLAM" to num(1), "COLOUR" to str("3"), "OBJNAM" to str("A")), Gdal.createPoint(p(300.0, 100.0)))
            addFeature("BOYLAT", props("CATLAM" to str("1"), "COLOUR" to str("4")), Gdal.createPoint(p(400.0, 100.0)))
        }
        assertEquals(4, decoded.features().size)
        assertEquals(listOf("CATLAM", "COLOUR", "OBJNAM"), decoded.features()[2].jsonObject["props"]!!.jsonObject.keys.toList())
    }

    @Test
    fun `layers are emitted in first-seen order`() {
        val decoded = case(
            "two_layers",
            "layers appear in the order their first feature was added and features keep insertion order inside a layer",
            "a LNDARE polygon, a SOUNDG point, a second LNDARE polygon",
        ) {
            addFeature("LNDARE", emptyMap(), Gdal.createPolygon(p(0.0, 0.0), p(0.0, 100.0), p(100.0, 100.0), p(100.0, 0.0), p(0.0, 0.0)))
            addFeature("SOUNDG", props("METERS" to num(3.4)), Gdal.createPoint(p(50.0, 50.0)))
            addFeature("LNDARE", emptyMap(), Gdal.createPolygon(p(200.0, 200.0), p(200.0, 300.0), p(300.0, 300.0), p(300.0, 200.0), p(200.0, 200.0)))
        }
        assertEquals(listOf("LNDARE", "SOUNDG"), decoded.layers().map { it.jsonObject["name"]!!.content() })
        assertTrue(decoded.features(0).size == 2)
    }
}
