import io.madrona.njord.geojson.Geometry
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * LB-1 `geo/ogr_geometry.json`: the `OgrGeometry` operations `TileEncoder` and `RegionExporter`
 * lean on - the chart-priority masking is `difference`/`intersection` against the chart coverage,
 * PLY boundaries come from `boundary()`, invalid S-57 polygons go through `makeValid()` at ingest,
 * and `centroidOfLargestPart` places region labels. The Rust port replaces all of these with the
 * `geo` crate (RUST_PORT_PLAN.md §4.2), so the exact vertex output matters: where `geo` differs
 * from GEOS the plan allows a tile-pixel tolerance, but the topology (part counts, emptiness,
 * validity, envelopes) must agree exactly.
 */
class OgrGeometryFixtureTest {

    @Serializable
    data class GeomOp(val op: String, val a: String, val b: String? = null, val n: Double? = null)

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
    }

    private fun geom(wkt: String): OgrGeometry = OgrGeometry.fromWkt4326(wkt) ?: error("bad WKT $wkt")
    private fun wkt(g: OgrGeometry?): JsonElement = g?.wkt?.let { JsonPrimitive(it) } ?: JsonNull

    private fun compute(op: GeomOp): JsonElement {
        val a = geom(op.a)
        val b = op.b?.let { geom(it) }
        return when (op.op) {
            "type" -> JsonPrimitive(a.type.name)
            "isValid" -> JsonPrimitive(a.isValid)
            "isEmpty" -> JsonPrimitive(a.isEmpty())
            "area" -> JsonPrimitive(a.area)
            "length" -> JsonPrimitive(a.length)
            "numGeometries" -> JsonPrimitive(a.numGeometries)
            "numCoordinates" -> JsonPrimitive(a.numCoordinates)
            "numInteriorRings" -> JsonPrimitive(a.numInteriorRings)
            "envelope" -> a.envelope().let { JsonArray(listOf(it.west, it.south, it.east, it.north).map { v -> JsonPrimitive(v) }) }
            "envelopeGeometry" -> wkt(a.envelopeGeometry(op.n?.toInt() ?: 0))
            "centroid" -> wkt(a.centroid())
            "centroidOfLargestPart" -> wkt(a.centroidOfLargestPart())
            "boundary" -> wkt(a.boundary())
            "makeValid" -> wkt(a.makeValid())
            "clone" -> wkt(a.clone())
            "simplify" -> wkt(a.simplify(op.n!!))
            "simplifyPreserveTopology" -> wkt(a.simplifyPreserveTopology(op.n!!))
            "intersection" -> wkt(a.intersection(b))
            "difference" -> wkt(a.difference(b!!))
            "union" -> wkt(a.union(b!!))
            "contains" -> JsonPrimitive(a.contains(b))
            "intersects" -> JsonPrimitive(a.intersects(b))
            "preparedContains" -> JsonPrimitive(a.prepare().contains(b))
            "preparedIntersects" -> JsonPrimitive(a.prepare().intersects(b))
            "coordinateSequence" -> JsonArray(a.coordinateSequence().map { JsonArray(listOf(JsonPrimitive(it.x), JsonPrimitive(it.y))) })
            "wkbHex" -> JsonPrimitive(a.wkb.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') })
            "wkbRoundTrip" -> wkt(OgrGeometry.fromWkb4326(a.wkb))
            "geoJson" -> a.geoJson()?.let { Json.encodeToJsonElement(Geometry.serializer(), it) } ?: JsonNull
            "exteriorRing" -> wkt(a.getExteriorRing())
            "interiorRing0" -> wkt(a.getInteriorRingN(0))
            else -> error("unknown op ${op.op}")
        }
    }

    private val square = "POLYGON ((0 0,10 0,10 10,0 10,0 0))"
    private val offsetSquare = "POLYGON ((5 5,15 5,15 15,5 15,5 5))"
    private val disjointSquare = "POLYGON ((20 20,30 20,30 30,20 30,20 20))"
    private val bowTie = "POLYGON ((0 0,10 10,10 0,0 10,0 0))"
    private val holed = "POLYGON ((0 0,10 0,10 10,0 10,0 0),(2 2,4 2,4 4,2 4,2 2))"
    private val multi = "MULTIPOLYGON (((0 0,10 0,10 10,0 10,0 0)),((20 20,22 20,22 22,20 22,20 20)))"
    private val line = "LINESTRING (-5 5,15 5)"
    private val jaggedLine = "LINESTRING (0 0,1 0.01,2 0,3 0.01,4 0,10 0)"
    private val point = "POINT (5 5)"
    private val outsidePoint = "POINT (50 50)"
    private val multiPoint = "MULTIPOINT ((1 1),(2 2))"
    private val emptyPoly = "POLYGON EMPTY"
    private val collection = "GEOMETRYCOLLECTION (POINT (1 1),LINESTRING (0 0,1 1))"
    private val tile = "POLYGON ((-122.4041748046875 47.27922900257082,-122.398681640625 47.27922900257082,-122.398681640625 47.28295557691229,-122.4041748046875 47.28295557691229,-122.4041748046875 47.27922900257082))"
    private val chartCover = "POLYGON ((-122.41 47.27,-122.40 47.27,-122.40 47.29,-122.41 47.29,-122.41 47.27))"

    private val inputs: List<GeomOp> = buildList {
        listOf(square, bowTie, holed, multi, line, point, multiPoint, emptyPoly, collection).forEach { g ->
            listOf("type", "isValid", "isEmpty", "area", "length", "numGeometries", "numCoordinates", "envelope", "wkbHex", "wkbRoundTrip", "geoJson", "clone", "coordinateSequence").forEach {
                add(GeomOp(it, g))
            }
        }
        listOf(square, holed, multi).forEach { add(GeomOp("numInteriorRings", it)); add(GeomOp("exteriorRing", it)); add(GeomOp("interiorRing0", it)) }
        listOf(square, holed, multi, line, point, collection).forEach { add(GeomOp("boundary", it)); add(GeomOp("centroid", it)); add(GeomOp("centroidOfLargestPart", it)) }
        listOf(square, bowTie, holed, emptyPoly).forEach { add(GeomOp("makeValid", it)) }
        listOf(0, 1).forEach { add(GeomOp("envelopeGeometry", square, n = it.toDouble())); add(GeomOp("envelopeGeometry", line, n = it.toDouble())) }
        listOf(0.1, 0.5).forEach { add(GeomOp("simplify", jaggedLine, n = it)); add(GeomOp("simplifyPreserveTopology", jaggedLine, n = it)); add(GeomOp("simplify", holed, n = it)) }
        listOf(offsetSquare, disjointSquare, holed, line, point, outsidePoint, emptyPoly).forEach { other ->
            listOf("intersection", "difference", "union", "contains", "intersects", "preparedContains", "preparedIntersects").forEach { add(GeomOp(it, square, other)) }
        }
        // the shapes the tile path actually combines: a tile clip polygon against a chart coverage
        listOf("intersection", "difference", "intersects").forEach { add(GeomOp(it, tile, chartCover)); add(GeomOp(it, chartCover, tile)) }
        add(GeomOp("difference", square, bowTie))
        add(GeomOp("intersection", line, square))
        add(GeomOp("intersection", multi, offsetSquare))
    }

    @Test
    fun `geometry operations match the committed fixture`() {
        val table = fixtures.table(
            "geo/ogr_geometry.json",
            gdalHeader(
                pins = "OgrGeometry over GDAL/GEOS: type, validity, emptiness, area/length (planar, degrees), envelopes, centroids, boundary, makeValid of a bow-tie, simplify vs simplifyPreserveTopology, intersection/difference/union/contains/intersects incl. against an empty polygon, WKB/WKT/GeoJSON round trips; difference(other) is null when `other` is invalid",
                decisions = listOf("LB-1", "TE-10"),
                generator = "OgrGeometryFixtureTest",
                source = "hand-written WKT incl. a real z16 tile clip polygon and a chart coverage",
            ),
            inputs,
        ) { compute(it) }
        assertEquals(inputs.size, table.cases.size)
    }

    @Test
    fun `the anchors the tile path relies on`() {
        assertEquals(false, geom(bowTie).isValid)
        assertEquals(true, geom(bowTie).makeValid()!!.isValid)
        // difference against an invalid mask is refused rather than guessed
        assertEquals(null, geom(square).difference(geom(bowTie)))
        assertEquals(true, geom(square).difference(geom(disjointSquare))!!.let { it.isValid && !it.isEmpty() })
        assertEquals(true, geom(square).intersection(geom(disjointSquare))!!.isEmpty())
    }
}
