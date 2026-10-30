import io.madrona.njord.model.ChartInsert
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The GDAL read contract for S-57 cells (RUST_PORT_PLAN.md §5.3): everything downstream - the
 * `features.props` JSONB, LNAM/LNAM_REFS, METERS on soundings, MINZ/MAXZ, DSID/DSPM metadata - is
 * shaped by GDAL's S57 driver under the options set in `Gdal.initialize()`. The `gdal` crate must
 * reproduce these files exactly, and a future pure-Rust reader is measured against them.
 *
 * - P-IN-1 `s57/chart_insert.json` - `chartInsertInfo()` per cell
 * - P-IN-2 `s57/<cell>/features/<LAYER>.json` - every inserted feature's props and WKT after `makeValid()`
 * - P-IN-3 `s57/layer_counts.json` - `featureCount(exLayers)` and per-layer counts
 * - P-IN-4 `s57/update_cell/` - the base cell without its `.001`-`.004` files vs the updated one
 */
class S57FixtureTest {

    private val cells = listOf(
        "fixtures/US5WA22M/US5WA22M.000",
        "fixtures/synthetic/US4ZZ01M.000",
        "fixtures/synthetic/US5ZZ02M.000",
    )

    @BeforeTest
    fun beforeEach() {
        Gdal.initialize()
    }

    private fun open(relativePath: String): OgrS57Dataset {
        val file = fixtures.file(relativePath)
        assertTrue(file.exists(), "missing fixture $file")
        return OgrS57Dataset(file)
    }

    private fun OgrS57Dataset.insertInfo(): ChartInsert = assertIs<InsertSuccess<ChartInsert>>(chartInsertInfo()).value

    private fun OgrS57Dataset.insertedLayers(): List<String> = layerNames().filter { it !in ingestExcludedLayers }

    @Test
    fun `P-IN-1 chartInsertInfo for every fixture cell`() {
        val table = fixtures.table(
            "s57/chart_insert.json",
            gdalHeader(
                pins = "OgrS57Dataset.chartInsertInfo(): name = DSID_DSNM, scale = DSPM_CSCL, zoom = scaleToZoomInt(scale, coverage centre latitude), updated/issued = DSID_UADT/ISDT after updates, coverage = the CATCOV=1 M_COVR geometry (union as MultiPolygon when several), every DSID/DSPM/DSSI prop, chart_txt = every sibling .TXT read in the DSSI charset",
                decisions = listOf("IN-2", "D7"),
                generator = "S57FixtureTest.P-IN-1",
                source = "fixtures/US5WA22M (edition 40 + .001-.004) and the two synthetic cells",
            ),
            cells,
        ) { open(it).insertInfo() }
        assertEquals(3, table.cases.size)
    }

    @Test
    fun `P-IN-2 every inserted feature of every layer`() {
        val perCell = mapOf(
            "fixtures/US5WA22M/US5WA22M.000" to "s57/US5WA22M/features",
            "fixtures/synthetic/US4ZZ01M.000" to "s57/synthetic/US4ZZ01M/features",
            "fixtures/synthetic/US5ZZ02M.000" to "s57/synthetic/US5ZZ02M/features",
        )
        perCell.forEach { (cell, dir) ->
            val ds = open(cell)
            val layers = ds.insertedLayers()
            assertTrue(layers.isNotEmpty(), "$cell has no object layers")
            layers.forEach { layer ->
                val features = ds.getLayer(layer)!!.insertedFeatures()
                fixtures.table(
                    "$dir/$layer.json",
                    gdalHeader(
                        pins = "GDAL S57 driver output for $layer under RETURN_PRIMITIVES=OFF,RETURN_LINKAGES=OFF,LNAM_REFS=ON,UPDATES=APPLY,SPLIT_MULTIPOINT=ON,RECODE_BY_DSSI=ON,ADD_SOUNDG_DEPTH=ON: OgrFeature.properties (field type -> JSON mapping, METERS on SOUNDG, MINZ/MAXZ from SCAMIN/SCAMAX at the feature's centre latitude) and the WKT after makeValid(), in GDAL's feature order; the input is the feature index",
                        decisions = listOf("IN-1", "LB-2"),
                        generator = "S57FixtureTest.P-IN-2",
                        source = cell,
                    ),
                    features.indices.toList(),
                ) { features[it] }
            }
        }
    }

    @Serializable
    data class LayerCounts(val excluded: List<String>, val total: Long, val layers: Map<String, Long>)

    @Test
    fun `P-IN-3 feature counts per layer`() {
        fixtures.table(
            "s57/layer_counts.json",
            gdalHeader(
                pins = "OgrS57Dataset.layerNames() order and OGR_L_GetFeatureCount per layer; total = featureCount(ChartIngest.exLayers), the number ChartIngest reports as totalFeatures; the excluded set is pinned alongside",
                decisions = listOf("IN-1"),
                generator = "S57FixtureTest.P-IN-3",
                source = "all three fixture cells",
            ),
            cells,
        ) { cell ->
            val ds = open(cell)
            LayerCounts(
                excluded = ingestExcludedLayers,
                total = ds.featureCount(ingestExcludedLayers.toSet()),
                layers = ds.layerNames().associateWith { ds.getLayer(it)!!.featureCount },
            )
        }
    }

    @Serializable
    data class DsidState(
        val name: String,
        val updated: String,
        val issued: String,
        val dsidUpdn: String?,
        val dsidEdtn: String?,
        val dsidUadt: String?,
        val dsidIsdt: String?,
        val featureCount: Long,
        val chartTxt: List<String>,
    )

    @Serializable
    data class Change(val layer: String, val lnam: String, val before: List<FeatureOut>, val after: List<FeatureOut>)

    /** `US5WA22M.000` alone, so GDAL has no `.001`-`.004` to apply and no `.TXT` to attach. */
    private fun baseCellOnly(): OgrS57Dataset {
        val dir = File("./build/tmp/update_cell/US5WA22M").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        val base = File(dir, "US5WA22M.000")
        base.writeBytes(fixtures.file("fixtures/US5WA22M/US5WA22M.000").readData())
        return OgrS57Dataset(base)
    }

    private fun OgrS57Dataset.dsidState(): DsidState {
        val info = insertInfo()
        fun prop(key: String) = (info.dsidProps[key] as? JsonPrimitive)?.content
        return DsidState(
            name = info.name,
            updated = info.updated,
            issued = info.issued,
            dsidUpdn = prop("DSID_UPDN"),
            dsidEdtn = prop("DSID_EDTN"),
            dsidUadt = prop("DSID_UADT"),
            dsidIsdt = prop("DSID_ISDT"),
            featureCount = featureCount(ingestExcludedLayers.toSet()),
            chartTxt = info.chartTxt.keys.sorted(),
        )
    }

    /** `(layer, LNAM)` -> the features carrying it, sorted by WKT: SPLIT_MULTIPOINT gives several SOUNDG features one LNAM. */
    private fun OgrS57Dataset.byLnam(): Map<Pair<String, String>, List<FeatureOut>> {
        val out = mutableMapOf<Pair<String, String>, MutableList<FeatureOut>>()
        insertedLayers().forEach { layer ->
            getLayer(layer)!!.insertedFeatures().forEach { f ->
                val lnam = (f.props["LNAM"] as? JsonPrimitive)?.content ?: return@forEach
                out.getOrPut(layer to lnam) { mutableListOf() }.add(f)
            }
        }
        return out.mapValues { (_, v) -> v.sortedBy { it.wkt ?: "" } }
    }

    @Test
    fun `P-IN-4 update files change the DSID record and a handful of features`() {
        val base = baseCellOnly()
        val updated = open("fixtures/US5WA22M/US5WA22M.000")

        fixtures.table(
            "s57/update_cell/dsid.json",
            gdalHeader(
                pins = "UPDATES=APPLY: with .001-.004 beside the .000 GDAL applies them, so DSID_UPDN/EDTN/UADT/ISDT and the feature count change and the .TXT siblings become chart_txt; the lone .000 reads as update 0 with no chart_txt",
                decisions = listOf("IN-10"),
                generator = "S57FixtureTest.P-IN-4",
                source = "fixtures/US5WA22M/US5WA22M.000 copied alone (base) vs in place with its updates (updated)",
            ),
            listOf("base", "updated"),
        ) { if (it == "base") base.dsidState() else updated.dsidState() }

        val before = base.byLnam()
        val after = updated.byLnam()
        val changes = mapOf(
            "added" to (after.keys - before.keys).sortedWith(compareBy({ it.first }, { it.second }))
                .map { Change(it.first, it.second, emptyList(), after[it]!!) },
            "removed" to (before.keys - after.keys).sortedWith(compareBy({ it.first }, { it.second }))
                .map { Change(it.first, it.second, before[it]!!, emptyList()) },
            "changed" to (before.keys intersect after.keys).filter { before[it] != after[it] }
                .sortedWith(compareBy({ it.first }, { it.second }))
                .map { Change(it.first, it.second, before[it]!!, after[it]!!) },
        )
        val table = fixtures.table(
            "s57/update_cell/changed_features.json",
            gdalHeader(
                pins = "the features (keyed by layer + LNAM) that US5WA22M.001-.004 add, remove or modify when GDAL applies them; a key with several features (SPLIT_MULTIPOINT soundings) lists them sorted by WKT",
                decisions = listOf("IN-10"),
                generator = "S57FixtureTest.P-IN-4",
                source = "fixtures/US5WA22M: lone .000 vs .000 + .001-.004",
            ),
            listOf("added", "removed", "changed"),
        ) { changes[it]!! }
        assertTrue(changes.values.any { it.isNotEmpty() }, "four update files must change something")
        assertEquals(3, table.cases.size)
    }
}
