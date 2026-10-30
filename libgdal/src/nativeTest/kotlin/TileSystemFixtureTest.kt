import io.madrona.njord.geojson.Position
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P-TE-1 `geo/tile_system.json`: the Web Mercator arithmetic every tile, region archive and
 * SCAMIN/SCAMAX zoom derivation in the server depends on, pinned over a grid that deliberately
 * includes the inputs the formulas clamp - latitudes past ±85.05112878, longitudes past ±180,
 * z0 (one tile) and z24 (the deepest zoom the port validates, decision D8).
 */
class TileSystemFixtureTest {

    @Serializable
    data class TileOp(
        val op: String,
        val lng: Double? = null,
        val lat: Double? = null,
        val z: Int? = null,
        val x: Int? = null,
        val y: Int? = null,
        val scale: Double? = null,
        val zoom: Double? = null,
        val tweak: Double? = null,
        val expandPixels: Int? = null,
    )

    private val tileSystem = TileSystem()

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
    }

    private fun Position.json(): JsonElement = JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y)))

    private fun compute(op: TileOp): JsonElement = when (op.op) {
        "mapSize" -> JsonPrimitive(tileSystem.mapSize(op.z!!))
        "latLngToTileXy" -> tileSystem.latLngToTileXy(op.lng!!, op.lat!!, op.z!!).json()
        "latLngToTileBoundedXy" -> tileSystem.latLngToTileBoundedXy(op.lng!!, op.lat!!, op.x!!, op.y!!, op.z!!).json()
        "scaleToZoom" -> JsonPrimitive(tileSystem.scaleToZoom(op.scale!!, op.lat!!))
        "zoomToScale" -> JsonPrimitive(tileSystem.zoomToScale(op.zoom!!, op.lat!!))
        "scaleToZoomInt" -> JsonPrimitive(
            op.tweak?.let { tileSystem.scaleToZoomInt(op.scale!!, op.lat!!, it) }
                ?: tileSystem.scaleToZoomInt(op.scale!!, op.lat!!)
        )
        "createTileClipPolygon" -> JsonPrimitive(
            tileSystem.createTileClipPolygon(op.x!!, op.y!!, op.z!!, op.expandPixels ?: 0).wkt
        )
        else -> error("unknown op ${op.op}")
    }

    private val lngs = listOf(-200.0, -180.0, -179.999999, -122.4041748046875, -0.0000001, 0.0, 122.4, 179.999999, 180.0, 200.0)
    private val lats = listOf(-90.0, -85.05112878, -85.0, -47.3, 0.0, 47.27922900257082, 47.28295557691229, 85.0, 85.05112878, 90.0)
    private val zooms = listOf(0, 1, 5, 11, 16, 24)

    private val inputs: List<TileOp> = buildList {
        zooms.forEach { add(TileOp("mapSize", z = it)) }

        zooms.forEach { z -> lats.forEach { lat -> lngs.forEach { lng -> add(TileOp("latLngToTileXy", lng = lng, lat = lat, z = z)) } } }

        // the z16 tile TileSystemTest pins, its corners/centre and points just outside it. z19 is
        // the deepest level whose envelope is finite: mapSize(z) = 4096 shl z overflows Int at z20.
        val bounded = listOf(
            Triple(10485, 22975, 16), Triple(0, 0, 0), Triple(1, 1, 1), Triple(2621, 5743, 14),
            Triple(0, 0, 19), Triple(524287, 524287, 19),
        )
        bounded.forEach { (x, y, z) ->
            val env = tileSystem.createTileClipPolygon(x, y, z).envelope()
            val midLng = (env.west + env.east) / 2
            val midLat = (env.south + env.north) / 2
            listOf(
                env.west to env.north, env.east to env.north, env.east to env.south, env.west to env.south,
                midLng to midLat, env.west - 1.0 to env.north + 1.0, env.east + 1.0 to env.south - 1.0,
                -180.0 to -90.0, 180.0 to 90.0,
            ).forEach { (lng, lat) -> add(TileOp("latLngToTileBoundedXy", lng = lng, lat = lat, x = x, y = y, z = z)) }
        }

        val scales = listOf(
            500.0, 1_128.5, 5_000.0, 10_000.0, 15_000.0, 22_000.0, 35_000.0, 50_000.0, 80_000.0, 150_000.0,
            350_000.0, 1_000_000.0, 3_500_000.0, 10_000_000.0, 50_000_000.0, 110_000_000.0, 591_658_710.91,
        )
        val scaleLats = listOf(0.0, 47.6, 47.84485177047364, 70.0, 85.05112878)
        scaleLats.forEach { lat ->
            scales.forEach { scale ->
                add(TileOp("scaleToZoom", scale = scale, lat = lat))
                add(TileOp("scaleToZoomInt", scale = scale, lat = lat))
                add(TileOp("scaleToZoomInt", scale = scale, lat = lat, tweak = 1.0))
            }
            (0..24).forEach { zoom -> add(TileOp("zoomToScale", zoom = zoom.toDouble(), lat = lat)) }
        }

        listOf(
            Triple(0, 0, 0), Triple(0, 0, 1), Triple(1, 1, 1), Triple(0, 0, 5), Triple(31, 31, 5),
            Triple(2621, 5743, 14), Triple(10485, 22975, 16), Triple(0, 0, 24),
            Triple(16777215, 16777215, 24), Triple(0, 16777215, 24), Triple(524287, 524287, 19),
        ).forEach { (x, y, z) ->
            listOf(0, 15).forEach { expand -> add(TileOp("createTileClipPolygon", x = x, y = y, z = z, expandPixels = expand)) }
        }
    }

    @Test
    fun `tile system arithmetic matches the committed fixture`() {
        val table = fixtures.table(
            "geo/tile_system.json",
            FixtureHeader(
                pins = "TileSystem: Web Mercator tile/pixel math with 4096px tiles, scale<->zoom at 96 dpi with the 0.92 tweak on the integer form, clip polygons incl. the 15px expansion; inputs past the Mercator limits are clamped. KNOWN DEFECT recorded as-is: mapSize(z) = 4096 shl z overflows Int from z20, so the z24 cases pin garbage (clamped pixels, infinite clip corners); the port should treat z >= 20 as a decision, not copy these values",
                decisions = listOf("TE-9", "D8"),
                generator = "TileSystemFixtureTest",
                source = "hand-written grid over the poles, the antimeridian, z0 and z24 plus the TileSystemTest expectations",
            ),
            inputs,
        ) { compute(it) }
        assertEquals(inputs.size, table.cases.size)
    }

    @Test
    fun `fixture reproduces the TileSystemTest anchors`() {
        // The hand-asserted values stay as a sanity check that the generator is not pinning garbage.
        val env = tileSystem.createTileClipPolygon(10485, 22975, 16).envelope()
        assertEquals(47.28295557691229, env.north)
        assertEquals(-122.4041748046875, env.west)
        assertEquals(10, tileSystem.scaleToZoomInt(150_000.0, 47.84485177047364))
        assertEquals(4096, tileSystem.mapSize(0))
        // the south pole clamps to the last pixel row, not past the map
        assertEquals(Position(0.0, (4096 - 1) / 4096.0), tileSystem.latLngToTileXy(-180.0, -90.0, 0))
    }
}
