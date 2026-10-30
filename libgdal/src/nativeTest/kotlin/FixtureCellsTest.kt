import io.madrona.njord.geojson.Polygon
import io.madrona.njord.geojson.doubleValue
import io.madrona.njord.model.ChartInsert
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The S-57 cells under `testdata/fixtures/` read through the same code path as a real ingest.
 *
 * Pins the groundwork fixtures of RUST_PORT_PHASE0_CHECKLIST.md §1: the copied NOAA cell is
 * intact (with its `.001`-`.004` updates applied) and the two synthetic cells made by
 * `testdata/tools/make_synthetic_cells.py` have overlapping coverage at different scales, so
 * TE-1 / P-TE-5 can rely on them.
 */
class FixtureCellsTest {

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
    }

    private fun open(relativePath: String): OgrS57Dataset {
        val file = fixtures.file(relativePath)
        assertTrue(file.exists(), "missing fixture $file")
        return OgrS57Dataset(file)
    }

    private fun OgrS57Dataset.insertInfo(): ChartInsert =
        assertIs<InsertSuccess<ChartInsert>>(chartInsertInfo()).value

    private fun ChartInsert.coverageBounds(): List<Double> {
        val ring = assertIs<Polygon>(covr.geometry).coordinates.single()
        val lon = ring.map { it.coordinates[0] }
        val lat = ring.map { it.coordinates[1] }
        return listOf(lon.min(), lat.min(), lon.max(), lat.max())
    }

    @Test
    fun `US5WA22M copy is the same cell libgdal tests use with updates applied`() {
        val ds = open("fixtures/US5WA22M/US5WA22M.000")
        assertEquals(67, ds.layerCount)
        assertEquals(5164, ds.featureCount())
        val info = ds.insertInfo()
        assertEquals("US5WA22M.000", info.name)
        assertEquals(15000, info.scale)
        assertEquals("4", info.dsidProps["DSID_UPDN"]?.jsonPrimitive?.content, "GDAL applies .001-.004 from the same directory")
        assertEquals("40", info.dsidProps["DSID_EDTN"]?.jsonPrimitive?.content)
        assertEquals(setOf("US5WA22A.TXT", "US5WA22B.TXT", "US5WA22C.TXT", "US5WA22D.TXT"), info.chartTxt.keys)
    }

    @Test
    fun `synthetic outer cell is the approach-scale chart`() {
        val ds = open("fixtures/synthetic/US4ZZ01M.000")
        val info = ds.insertInfo()
        assertEquals("US4ZZ01M.000", info.name)
        assertEquals(80000, info.scale)
        assertEquals(11, info.zoom)
        assertEquals("20260101", info.updated)
        assertEquals("20260101", info.issued)
        assertEquals(listOf(-123.2, 46.9, -122.8, 47.1), info.coverageBounds())
        assertEquals(6, ds.featureCount(setOf("DSID")))
        assertEquals(2, ds.getLayer("BOYLAT")?.featureCount)
        assertEquals(1, ds.getLayer("LIGHTS")?.featureCount)
        assertEquals(1, ds.getLayer("DEPARE")?.featureCount)
        assertEquals(1, ds.getLayer("DEPCNT")?.featureCount)
        val depare = ds.getLayer("DEPARE")!!.geoJson().features.single()
        assertEquals(10.0, depare.properties.doubleValue("DRVAL1"))
        assertEquals(20.0, depare.properties.doubleValue("DRVAL2"))
        assertEquals(5, assertIs<Polygon>(depare.geometry).coordinates.single().size, "one closed ring, no duplicate vertices")
    }

    @Test
    fun `synthetic inner cell is the harbour-scale chart nested inside the outer one`() {
        val outer = open("fixtures/synthetic/US4ZZ01M.000").insertInfo()
        val ds = open("fixtures/synthetic/US5ZZ02M.000")
        val info = ds.insertInfo()
        assertEquals("US5ZZ02M.000", info.name)
        assertEquals(10000, info.scale)
        assertEquals(14, info.zoom)
        val (w, s, e, n) = info.coverageBounds()
        assertEquals(listOf(-123.05, 46.97, -122.95, 47.03), listOf(w, s, e, n))
        val (ow, os, oe, on) = outer.coverageBounds()
        assertTrue(w > ow && s > os && e < oe && n < on, "inner coverage must sit strictly inside the outer")
        assertTrue(info.zoom > outer.zoom, "inner must be the larger-scale (higher-zoom) chart")
        // 5 S-57 records, but SPLIT_MULTIPOINT makes the four-depth SOUNDG count as four features.
        assertEquals(8, ds.featureCount(setOf("DSID")))
        assertEquals(1, ds.getLayer("BOYSPP")?.featureCount)
        val soundings = ds.getLayer("SOUNDG")!!.geoJson().features
        assertEquals(4, soundings.size, "SPLIT_MULTIPOINT turns the one SOUNDG record into a feature per depth")
        assertEquals(listOf(-0.6, 1.2, 3.4, 4.5), soundings.map { it.properties.doubleValue("METERS")!! }.sorted())
    }
}
