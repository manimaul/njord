@file:OptIn(ExperimentalForeignApi::class)

import io.madrona.njord.enccron.BundleBuilder
import io.madrona.njord.enccron.EncCatalogEntry
import io.madrona.njord.enccron.EncCronConfig
import io.madrona.njord.enccron.EncProdCatParser
import io.madrona.njord.enccron.EncTransport
import io.madrona.njord.enccron.batches
import io.madrona.njord.enccron.deletableOrphans
import io.madrona.njord.enccron.deleteOrphans
import io.madrona.njord.enccron.parseCatalogFile
import io.madrona.njord.enccron.selectOrphans
import io.madrona.njord.enccron.selectStale
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.PortDivergence
import io.madrona.njord.testdata.fixtures
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import platform.posix.setenv
import platform.posix.unsetenv
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Serialisable twin of [EncCatalogEntry] (which is a plain data class) incl. its derived keys. */
@Serializable
data class EntryOut(
    val cell: String,
    val edition: String,
    val url: String,
    val scale: Int?,
    val sizeMb: Double?,
    val revisionDate: String?,
    val publicationDate: String?,
    val chartName: String,
    val updateNumber: String?,
    val revisionKey: String?,
)

fun EncCatalogEntry.out() = EntryOut(cell, edition, url, scale, sizeMb, revisionDate, publicationDate, chartName, updateNumber, revisionKey)

/** A catalog entry as test input; `url` is derived from the cell like NOAA's. */
@Serializable
data class EntryIn(
    val cell: String,
    val edition: String = "1.0",
    val scale: Int? = 15000,
    val sizeMb: Double? = 0.1,
    val revision: String? = "20240101",
    val publication: String? = "20240102",
) {
    fun entry() = EncCatalogEntry(cell, edition, "https://example.com/$cell.zip", scale, sizeMb, revision, publication)
}

/**
 * The network as [BundleBuilder] and `deleteOrphans` see it: downloads are served from local zips
 * (or fail for cells listed in [failing]), signatures are counted, deletes are recorded.
 */
class FakeTransport(
    private val sources: Map<String, File> = emptyMap(),
    private val failing: Set<String> = emptySet(),
    private val gone: Set<String> = emptySet(),
) : EncTransport {
    val downloads = mutableListOf<String>()
    var signatureFetches = 0
    val deletes = mutableListOf<String>()

    override suspend fun download(url: String, dest: File): Long {
        downloads += url
        if (url in failing) throw IllegalStateException("download $url failed after 3 attempts")
        val src = sources[url] ?: throw IllegalStateException("404 $url")
        val bytes = src.readData()
        dest.writeBytes(bytes)
        return bytes.size.toLong()
    }

    override suspend fun fetchAdminSignature(baseUrl: String, user: String, pass: String): String {
        signatureFetches++
        return "signature-$signatureFetches"
    }

    override suspend fun deleteChart(baseUrl: String, name: String, signature: String): Boolean {
        deletes += name
        return name !in gone
    }
}

/**
 * P-EC-1 `enc_cron/parsed.json`: `EncProdCatParser` over the trimmed real catalog. The sample
 * keeps the `dataQualityInfo` decoy title (`"0"`), a synthetic record with no `<onLine>` (skipped,
 * not fatal) and the trailing `seriesMetadata` `MD_Metadata` that carries no cell fields at all.
 */
class EncProdCatFixtureTest {

    @Serializable
    data class Parsed(val recordsSeen: Int, val recordsSkipped: Int, val entries: List<EntryOut>)

    @Test
    fun `the trimmed NOAA catalog parses to the committed records`() {
        val table = fixtures.table(
            "enc_cron/parsed.json",
            FixtureHeader(
                pins = "EncProdCatParser: cell/edition/url/scale/transferSize and the revision vs publication CI_Date per MD_Metadata record, dates normalised to YYYYMMDD; records missing cell, edition or url are counted in recordsSkipped and dropped; the decoy sourceCitation title never leaks; derived chartName/updateNumber/revisionKey",
                decisions = listOf("EC-5"),
                generator = "EncProdCatFixtureTest",
                source = "testdata/enc_cron/catalog_sample.xml (testdata/tools/trim_catalog.py over a real ENCProdCat_19115.xml)",
            ),
            listOf("enc_cron/catalog_sample.xml"),
        ) { path ->
            val entries = mutableListOf<EncCatalogEntry>()
            val parser = EncProdCatParser { entries.add(it) }
            val bytes = fixtures.readBytes(path)
            ExpatSax(parser).use { sax ->
                // feed in small chunks so a text node split across buffers is exercised too
                var offset = 0
                while (offset < bytes.size) {
                    val n = minOf(4096, bytes.size - offset)
                    sax.feed(bytes.copyOfRange(offset, offset + n), n)
                    offset += n
                }
                sax.finish()
            }
            assertEquals(parseCatalogFile(fixtures.file(path)).map { it.out() }, entries.map { it.out() }, "parseCatalogFile must agree with the chunked parse")
            Parsed(parser.recordsSeen, parser.recordsSkipped, entries.map { it.out() })
        }
        val parsed = Json.decodeFromJsonElement(Parsed.serializer(), table.cases.single().expected)
        assertEquals(4, parsed.entries.size, "four complete real records")
        assertEquals(2, parsed.recordsSkipped, "the record without <onLine> and the seriesMetadata record")
        assertEquals(6, parsed.recordsSeen)
        assertTrue(parsed.entries.none { it.cell == "0" }, "the dataQualityInfo decoy title must never become a cell")
    }
}

/**
 * P-EC-2 `enc_cron/selection.json`: the pure selection logic (`selectStale`, `selectOrphans`,
 * `deletableOrphans`, `batches`) as one table, lifted from StaleSelectionTest / OrphanSelectionTest
 * / DeletableOrphanTest / BatchingTest so the Rust port reads the same cases those tests assert.
 */
class SelectionFixtureTest {

    @Serializable
    data class SelectionCase(
        val op: String,
        val catalog: List<EntryIn> = emptyList(),
        val have: Map<String, String> = emptyMap(),
        val orphans: List<String> = emptyList(),
        val scaleFilter: List<Int> = emptyList(),
        val bundleSizeCells: Int = 50,
        val maxBundleUncompressedBytes: Long = 256L * 1024 * 1024,
    )

    private fun compute(c: SelectionCase): JsonElement {
        val catalog = c.catalog.map { it.entry() }
        val config = encCronConfig(c.bundleSizeCells, c.maxBundleUncompressedBytes).copy(scaleFilter = c.scaleFilter)
        return when (c.op) {
            "selectStale" -> JsonArray(selectStale(catalog, c.have, config).map { JsonPrimitive(it.cell) })
            "selectOrphans" -> JsonArray(selectOrphans(catalog, c.have).map { JsonPrimitive(it) })
            "deletableOrphans" -> JsonArray(deletableOrphans(c.orphans, catalog).map { JsonPrimitive(it) })
            "batches" -> JsonArray(batches(catalog, config).map { b -> JsonArray(b.map { JsonPrimitive(it.cell) }) })
            else -> error("unknown op ${c.op}")
        }
    }

    private val key = "0:20240101:20240102"

    private val inputs = listOf(
        // selectStale
        SelectionCase("selectStale", listOf(EntryIn("A", "1.0"), EntryIn("B", "2.5"), EntryIn("C", "3.0")), mapOf("A.000" to key, "B.000" to "4:20240101:20240102")),
        SelectionCase("selectStale", listOf(EntryIn("A", publication = "20240909")), mapOf("A.000" to key)),
        SelectionCase("selectStale", listOf(EntryIn("A", revision = "20250505")), mapOf("A.000" to key)),
        SelectionCase("selectStale", listOf(EntryIn("A", "1.10")), mapOf("A.000" to "1:20240101:20240102")),
        SelectionCase("selectStale", listOf(EntryIn("A", "1.10")), mapOf("A.000" to "10:20240101:20240102")),
        SelectionCase("selectStale", listOf(EntryIn("A")), mapOf("A.000" to "9:20990101:20990102")),
        SelectionCase("selectStale", listOf(EntryIn("A", publication = null)), mapOf("A.000" to key)),
        SelectionCase("selectStale", listOf(EntryIn("A", scale = 15000), EntryIn("B", scale = 3000000)), scaleFilter = listOf(15000)),
        SelectionCase("selectStale", listOf(EntryIn("C"), EntryIn("A"), EntryIn("B"))),
        SelectionCase("selectStale", listOf(EntryIn("A", "44.2", revision = "20241218", publication = "20260121")), mapOf("A.000" to "2:20241218:20260121")),
        // selectOrphans
        SelectionCase("selectOrphans", listOf(EntryIn("A"), EntryIn("B")), mapOf("A.000" to key, "B.000" to key, "Z.000" to key)),
        SelectionCase("selectOrphans", listOf(EntryIn("A")), mapOf("A.000" to "9:20990101:20990102")),
        SelectionCase("selectOrphans", emptyList(), listOf("C.000", "A.000", "B.000").associateWith { key }),
        SelectionCase("selectOrphans", listOf(EntryIn("A", scale = 3000000)), mapOf("A.000" to key)),
        SelectionCase("selectOrphans", listOf(EntryIn("A")), emptyMap()),
        // deletableOrphans
        SelectionCase("deletableOrphans", listOf(EntryIn("US5WA22M"), EntryIn("US3AK4CM")), orphans = listOf("US5WA17M.000", "US5CA93M.000")),
        SelectionCase("deletableOrphans", listOf(EntryIn("US5WA22M"), EntryIn("US3AK4CM")), orphans = listOf("US5WA17M.000", "CA179001.000", "GB5X01SW.000")),
        SelectionCase("deletableOrphans", listOf(EntryIn("CA379141")), orphans = listOf("CA179001.000", "US5WA17M.000")),
        SelectionCase("deletableOrphans", listOf(EntryIn("US5WA22M")), orphans = listOf("us5wa17m.000")),
        SelectionCase("deletableOrphans", emptyList(), orphans = listOf("US5WA17M.000")),
        // batches
        SelectionCase("batches", (1..5).map { EntryIn("C$it", sizeMb = 0.01) }, bundleSizeCells = 2),
        SelectionCase("batches", (1..4).map { EntryIn("C$it", sizeMb = 1.0) }, bundleSizeCells = 100, maxBundleUncompressedBytes = 5L * 1024 * 1024),
        SelectionCase("batches", listOf(EntryIn("HUGE", sizeMb = 500.0)), bundleSizeCells = 100, maxBundleUncompressedBytes = 1024),
        SelectionCase("batches", (1..3).map { EntryIn("C$it", sizeMb = null) }, bundleSizeCells = 2),
        SelectionCase("batches", emptyList()),
        SelectionCase("batches", (1..17).map { EntryIn("C$it", sizeMb = 0.1) }, bundleSizeCells = 7),
    )

    @Test
    fun `selection logic matches the committed table`() {
        val table = fixtures.table(
            "enc_cron/selection.json",
            FixtureHeader(
                pins = "selectStale (opaque revision-key mismatch in either direction, null key always stale, scaleFilter, sorted by cell), selectOrphans (held but unlisted, sorted), deletableOrphans (producer prefix present in the catalog, case-insensitive), batches (cell count cap and 4x compressed-size byte cap, oversized cell alone)",
                decisions = listOf("EC-2", "EC-5"),
                generator = "SelectionFixtureTest",
                source = "cases of StaleSelectionTest, OrphanSelectionTest, DeletableOrphanTest and BatchingTest",
            ),
            inputs,
        ) { compute(it) }
        assertEquals(inputs.size, table.cases.size)
    }
}

/**
 * P-EC-3 `enc_cron/bundle_layout.json`: what `BundleBuilder.buildAndPublish` publishes into
 * `save/` from NOAA-shaped cell zips. The bundle must hold only `ENC_ROOT/<CELL>/…` (CATALOG.031
 * collides across cells, README/USERAGREEMENT are boilerplate), colocating `.000`, `.00N` and
 * `.TXT` so GDAL can apply updates; a failed download skips that cell and the rest still publish;
 * nothing at all → null and nothing published.
 *
 * Decision D3: Kotlin extracts a zip-slip entry (`ENC_ROOT/<CELL>/../../../evil.txt`) outside the
 * staging root and still bundles the cell; the port fails that cell and writes nothing outside
 * staging. Both are recorded, the port's as the `port` divergence.
 */
class BundleLayoutFixtureTest {

    @Serializable
    data class BundleCase(val name: String, val cells: List<String>, val failing: List<String> = emptyList(), val slip: List<String> = emptyList())

    @Serializable
    data class BundleOut(
        val published: Boolean,
        val bundle: String? = null,
        val entries: List<String>,
        val stagingRemoved: Boolean,
        val downloadRemoved: Boolean,
        /** files left under chartTempData that are neither the bundle nor the save dir, relative to chartTempData */
        val escaped: List<String>,
        val downloads: List<String>,
    )

    private val root = File("./build/tmp/bundle_layout")
    private val cellDirs = mapOf(
        "US5WA22M" to "fixtures/US5WA22M",
        "US4ZZ01M" to "fixtures/synthetic",
        "US5ZZ02M" to "fixtures/synthetic",
    )

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    /** A NOAA-style cell zip: `ENC_ROOT/<CELL>/<files>` plus the per-cell CATALOG.031 and the boilerplate text files. */
    private fun cellZip(cell: String, dir: File, slip: Boolean): File {
        val source = fixtures.file(cellDirs[cell] ?: error("unknown cell $cell"))
        val decoys = File(dir, "decoys").also { it.mkdirs() }
        val catalog = File(decoys, "CATALOG.031").also { it.write("catalog for $cell") }
        val readme = File(decoys, "README.TXT").also { it.write("boilerplate") }
        val agreement = File(decoys, "USERAGREEMENT.TXT").also { it.write("boilerplate") }
        val evil = File(decoys, "evil.txt").also { it.write("escaped $cell") }
        val zip = File(dir, "$cell.zip")
        ZipWriter.create(zip).use { w ->
            source.listFiles(false)
                .filter { it.isFile() && (it.name.startsWith(cell) || (cell == "US5WA22M" && it.name.endsWith(".TXT"))) }
                .sortedBy { it.name }
                .forEach { w.addFile("ENC_ROOT/$cell/${it.name}", it) }
            w.addFile("ENC_ROOT/CATALOG.031", catalog)
            w.addFile("README.TXT", readme)
            w.addFile("USERAGREEMENT.TXT", agreement)
            if (slip) w.addFile("ENC_ROOT/$cell/../../../evil.txt", evil)
        }
        return zip
    }

    private fun compute(c: BundleCase): BundleOut {
        val caseRoot = File(root, c.name).also { it.deleteRecursively(); it.mkdirs() }
        val zips = File(caseRoot, "zips").also { it.mkdirs() }
        val temp = File(caseRoot, "chart_temp").also { it.mkdirs() }
        val config = encCronConfig().copy(chartTempData = temp.getAbsolutePath().toString())
        // publishBundles() creates both before the first bundle; the builder relies on that
        config.workDir.mkdirs()
        config.saveDir.mkdirs()
        val sources = c.cells.associate { cell -> "https://example.com/$cell.zip" to cellZip(cell, zips, cell in c.slip) }
        val transport = FakeTransport(sources, failing = c.failing.map { "https://example.com/$it.zip" }.toSet())
        val entries = c.cells.map { EntryIn(it).entry() }

        val published = runBlocking { BundleBuilder(config, transport).buildAndPublish(entries, "${c.name}.zip") }

        val tempPrefix = "${temp.getAbsolutePath()}/"
        val leftovers = temp.listFiles(true)
            .filter { it.isFile() }
            .map { it.getAbsolutePath().toString().removePrefix(tempPrefix) }
            .filter { !it.startsWith("save/") }
            .sorted()
        return BundleOut(
            published = published != null,
            bundle = published?.getAbsolutePath()?.toString()?.removePrefix(tempPrefix),
            entries = published?.let { ZipFile(it).entries().map { e -> e.name() }.sorted() } ?: emptyList(),
            stagingRemoved = !File(config.workDir, "staging").exists(),
            downloadRemoved = !File(config.workDir, "download").exists(),
            escaped = leftovers,
            downloads = transport.downloads,
        )
    }

    private val inputs = listOf(
        BundleCase("two_cells", listOf("US4ZZ01M", "US5ZZ02M")),
        BundleCase("real_cell_with_updates", listOf("US5WA22M")),
        BundleCase("one_failing", listOf("US4ZZ01M", "US5ZZ02M"), failing = listOf("US4ZZ01M")),
        BundleCase("all_failing", listOf("US4ZZ01M", "US5ZZ02M"), failing = listOf("US4ZZ01M", "US5ZZ02M")),
        BundleCase("zip_slip", listOf("US4ZZ01M", "US5ZZ02M"), slip = listOf("US4ZZ01M")),
    )

    @Test
    fun `bundle layout matches the committed fixture`() {
        val table = fixtures.table(
            "enc_cron/bundle_layout.json",
            FixtureHeader(
                pins = "BundleBuilder.buildAndPublish: only ENC_ROOT/<CELL>/… entries (no CATALOG.031, README.TXT, USERAGREEMENT.TXT), .000/.00N/.TXT colocated, published atomically into save/, staging and download dirs removed, a failed cell skipped while the others publish, all failing -> null with nothing published. D3 (zip-slip): Kotlin writes the escaping entry outside staging and keeps the cell; the port fails that cell",
                decisions = listOf("EC-1", "D3"),
                generator = "BundleLayoutFixtureTest",
                source = "zips built with ZipWriter from fixtures/US5WA22M and fixtures/synthetic in NOAA's ENC_ROOT layout",
            ),
            inputs,
            port = { c ->
                if (c.slip.isEmpty()) null else {
                    val kotlin = compute(c)
                    PortDivergence(
                        "D3",
                        kotlin.copy(
                            entries = kotlin.entries.filter { e -> c.slip.none { s -> e.startsWith("ENC_ROOT/$s/") } },
                            escaped = emptyList(),
                        ),
                    )
                }
            },
        ) { compute(it) }
        val slip = Json.decodeFromJsonElement(BundleOut.serializer(), table.cases.last().expected)
        assertEquals(listOf("enc_cron/evil.txt"), slip.escaped, "Kotlin writes the zip-slip entry into the work dir, outside staging (D3)")
        assertTrue(slip.entries.any { it.startsWith("ENC_ROOT/US4ZZ01M/") }, "Kotlin still bundles the cell")
        val allFailing = Json.decodeFromJsonElement(BundleOut.serializer(), table.cases[3].expected)
        assertEquals(false, allFailing.published)
        assertEquals(emptyList(), allFailing.escaped)
    }
}

/**
 * P-EC-4 `enc_cron/config.json`: `EncCronConfig.load` layering - `config/enc_cron.json`, then the
 * `ENC_CRON_OPTS` shallow JSON merge, then credentials from `ENC_CRON_ADMIN_USER/PASS` - plus the
 * derived `saveDir`, `workDir` and `njordBaseUrl`. Required fields with no default make a missing
 * file a hard error, which is recorded as `throws`.
 */
class ConfigFixtureTest {

    @Serializable
    data class ConfigCase(
        val name: String,
        val file: String? = null,
        val opts: String? = null,
        val envUser: String? = null,
        val envPass: String? = null,
        val resourcesDir: Boolean = true,
    )

    private val root = File("./build/tmp/enc_cron_config")
    private val json = Json

    @AfterTest
    fun cleanup() {
        clearEnv()
        root.deleteRecursively()
    }

    private fun clearEnv() {
        unsetenv("ENC_CRON_OPTS")
        unsetenv("ENC_CRON_ADMIN_USER")
        unsetenv("ENC_CRON_ADMIN_PASS")
    }

    private fun compute(c: ConfigCase): JsonElement {
        clearEnv()
        val dir = File(root, c.name).also { it.deleteRecursively(); it.mkdirs() }
        c.file?.let { File(dir, "config").mkdirs(); File(dir, "config/enc_cron.json").write(it) }
        c.opts?.let { setenv("ENC_CRON_OPTS", it, 1) }
        c.envUser?.let { setenv("ENC_CRON_ADMIN_USER", it, 1) }
        c.envPass?.let { setenv("ENC_CRON_ADMIN_PASS", it, 1) }
        return runCatching { EncCronConfig.load(if (c.resourcesDir) dir else null) }.fold(
            onSuccess = { config ->
                buildJsonObject {
                    put("config", json.encodeToJsonElement(config))
                    put("saveDir", config.saveDir.path.toString())
                    put("workDir", config.workDir.path.toString())
                    put("njordBaseUrl", config.njordBaseUrl?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                }
            },
            onFailure = { buildJsonObject { put("throws", it::class.simpleName ?: "Throwable") } },
        )
    }

    /** Gradle runs native tests with the module directory as cwd. */
    private val shipped = File("./src/nativeMain/resources/config/enc_cron.json").readContents()

    private val minimal = """{"catalogUrl":"https://charts.noaa.gov/ENCs/ENCProdCat_19115.xml","chartEditionsUrl":"http://njord-chart-svc/v1/chart_editions","chartTempData":"/mnt/njord/charts","maxCellsPerRun":500,"bundleSizeCells":50,"maxBundleUncompressedBytes":268435456,"scaleFilter":[],"requestTimeoutSeconds":600,"maxRetries":3}"""

    private val inputs = listOf(
        ConfigCase("shipped_defaults", file = shipped),
        ConfigCase("minimal_file", file = minimal),
        ConfigCase("opts_shallow_merge", file = minimal, opts = """{"maxCellsPerRun":5,"scaleFilter":[15000,80000],"deleteOrphans":false,"chartEditionsUrl":"https://openenc.com/v1/chart_editions"}"""),
        ConfigCase("opts_blank_is_ignored", file = minimal, opts = "   "),
        ConfigCase("credentials_from_env", file = minimal, envUser = "cron", envPass = "secret"),
        ConfigCase("env_credentials_win_over_json", file = """${minimal.dropLast(1)},"adminUser":"json-user","adminPass":"json-pass"}""", envUser = "env-user"),
        ConfigCase("blank_env_credentials_fall_back_to_json", file = """${minimal.dropLast(1)},"adminUser":"json-user","adminPass":"json-pass"}""", envUser = "", envPass = ""),
        ConfigCase("opts_alone_without_file", opts = minimal),
        ConfigCase("no_v1_in_editions_url", file = minimal, opts = """{"chartEditionsUrl":"http://localhost:9000/editions"}"""),
        ConfigCase("missing_file_throws"),
        ConfigCase("no_resources_dir_throws", resourcesDir = false),
        ConfigCase("unknown_keys_ignored", file = """${minimal.dropLast(1)},"futureKnob":true}"""),
    )

    @Test
    fun `config loading matches the committed table`() {
        val table = fixtures.table(
            "enc_cron/config.json",
            FixtureHeader(
                pins = "EncCronConfig.load: config/enc_cron.json then ENC_CRON_OPTS as a shallow top-level merge (blank ignored), then ENC_CRON_ADMIN_USER/PASS when non-blank; unknown keys ignored; required fields without defaults make a missing/absent file throw; saveDir/workDir/njordBaseUrl derivation",
                decisions = listOf("EC-3"),
                generator = "ConfigFixtureTest",
                source = "the shipped enc_cron/src/nativeMain/resources/config/enc_cron.json plus hand-written variants",
            ),
            inputs,
        ) { compute(it) }
        assertEquals(inputs.size, table.cases.size)
    }
}

/**
 * P-EC-5 `enc_cron/delete_orphans.json`: the guard ordering in `deleteOrphans`. Every guard that
 * refuses must refuse *before* a signature is fetched, and the `maxOrphanDeletes` ceiling must
 * abandon the whole pass rather than delete the first N - a truncated catalog looks exactly like a
 * mass withdrawal (EC-2).
 */
class DeleteOrphansFixtureTest {

    @Serializable
    data class DeleteCase(
        val name: String,
        val deleteOrphans: Boolean = true,
        val chartEditionsUrl: String = "http://njord-chart-svc/v1/chart_editions",
        val adminUser: String = "cron",
        val adminPass: String = "secret",
        val maxOrphanDeletes: Int = 25,
        val dryRun: Boolean = false,
        val orphans: List<String>,
        val catalogCells: List<String> = listOf("US5WA22M", "US3AK4CM"),
        val gone: List<String> = emptyList(),
    )

    @Serializable
    data class DeleteOut(val signatureFetches: Int, val deleteCalls: List<String>)

    private fun compute(c: DeleteCase): DeleteOut {
        val config = encCronConfig().copy(
            deleteOrphans = c.deleteOrphans,
            chartEditionsUrl = c.chartEditionsUrl,
            adminUser = c.adminUser,
            adminPass = c.adminPass,
            maxOrphanDeletes = c.maxOrphanDeletes,
        )
        val transport = FakeTransport(gone = c.gone.toSet())
        runBlocking { deleteOrphans(c.orphans, c.catalogCells.map { EntryIn(it).entry() }, config, transport, c.dryRun) }
        return DeleteOut(transport.signatureFetches, transport.deletes)
    }

    private val two = listOf("US5WA17M.000", "US5CA93M.000")

    private val inputs = listOf(
        DeleteCase("disabled", deleteOrphans = false, orphans = two),
        DeleteCase("no_base_url", chartEditionsUrl = "http://localhost:9000/editions", orphans = two),
        DeleteCase("no_user", adminUser = "", orphans = two),
        DeleteCase("no_password", adminPass = "", orphans = two),
        DeleteCase("only_foreign_orphans", orphans = listOf("CA179001.000", "GB5X01SW.000")),
        DeleteCase("over_ceiling_deletes_none", maxOrphanDeletes = 1, orphans = two),
        DeleteCase("at_ceiling_deletes_all", maxOrphanDeletes = 2, orphans = two),
        DeleteCase("dry_run", dryRun = true, orphans = two),
        DeleteCase("happy_path", orphans = two),
        DeleteCase("foreign_excluded_from_count_and_calls", maxOrphanDeletes = 2, orphans = two + listOf("CA179001.000")),
        DeleteCase("already_gone_is_not_an_error", orphans = two, gone = listOf("US5CA93M.000")),
        DeleteCase("empty", orphans = emptyList()),
    )

    @Test
    fun `guard ordering matches the committed decision table`() {
        val table = fixtures.table(
            "enc_cron/delete_orphans.json",
            FixtureHeader(
                pins = "deleteOrphans guard order: disabled -> no njordBaseUrl -> blank credentials -> producer filter -> deletable.size > maxOrphanDeletes abandons the whole pass -> dry-run lists only; otherwise exactly one signature fetch then one DELETE per deletable chart in order, a false result (already gone) is not an error",
                decisions = listOf("EC-2"),
                generator = "DeleteOrphansFixtureTest",
                source = "hand-written decision table",
            ),
            inputs,
        ) { compute(it) }
        val over = Json.decodeFromJsonElement(DeleteOut.serializer(), table.cases[5].expected)
        assertEquals(DeleteOut(0, emptyList()), over, "over the ceiling nothing is fetched or deleted")
        val happy = Json.decodeFromJsonElement(DeleteOut.serializer(), table.cases[8].expected)
        assertEquals(DeleteOut(1, two), happy)
    }
}
