import io.madrona.njord.endpoints.resourceName
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.PortDivergence
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P-SEC-3: every place a caller-supplied name becomes a filesystem path, in one decision table
 * (`testdata/security/paths.json`, discriminated by `kind`):
 *
 * - `content_resource` - `/v1/content/{…}` segments -> resource name or null (refused)
 * - `archive_name` - `/v1/regions/{archive}` name -> accepted by the handler's traversal check
 * - `upload_filename` - `POST /v1/enc_save?filename=` -> where the zip lands relative to `save/`
 *   (defect D2: Kotlin writes `File(saveDir, filename)` verbatim; the port accepts only a bare
 *   `.zip` name and answers 400 otherwise)
 * - `zip_entry` - an entry name in an uploaded zip -> where `unzipToPath` writes it relative to the
 *   extraction directory, observed by really extracting a zip built with ZipWriter (defect D3:
 *   zip-slip; the port rejects the entry and aborts the extraction)
 *
 * Paths outside the target directory are reported as `../…` relative paths, never absolute.
 */
class SecurityPathsFixtureTest {

    @BeforeTest
    fun before() = TestEnv.init()

    @Serializable
    data class PathInput(val kind: String, val segments: List<String>? = null, val name: String? = null)

    /** [target] relative to [root] (`.` for root itself); outside paths climb with `..`, never absolute. */
    private fun relativeTo(root: File, target: File): String {
        val rootPath = root.getAbsolutePath().toString()
        val path = File(target.normalizePath().toString()).getAbsolutePath().toString().let { File(it).normalizePath().toString() }
        fun under(dir: String) = if (dir == "/") "/" else "$dir/"
        if (path == rootPath) return "."
        if (path.startsWith(under(rootPath))) return path.removePrefix(under(rootPath))
        var up = root.parentFile()
        var dots = ".."
        while (up != null) {
            val upPath = up.getAbsolutePath().toString()
            if (path == upPath) return dots
            if (path.startsWith(under(upPath))) return "$dots/" + path.removePrefix(under(upPath))
            up = up.parentFile()
            dots += "/.."
        }
        return path
    }

    private val uploadNames = listOf(
        "US5WA22M.zip", "enc_bundle-20260101-0001.zip", "A.ZIP", "with space.zip", "../escape.zip", "../../escape.zip",
        "dir/x.zip", "/abs/x.zip", "x.txt", "x", "", ".", "..", "x\\y.zip", ".zip", "x.zip.tmp",
    )

    private val zipEntries = listOf(
        "ENC_ROOT/US5ZZ99M/US5ZZ99M.000", "US5ZZ99M.000", "__MACOSX/._x", "dir/", "../escape.txt", "a/../../escape2.txt",
        "a/../inside.txt", "/abs.txt", "back\\slash.txt", "./dotted.txt",
    )

    private val archiveNames = listOf(
        "REGION_04_2026-04-14T07-00-31.mbtiles", "REGION_04_2026-04-14T07-00-31.mbtiles.sha256", "WORLD_2026-01-01T00-00-00.mbtiles",
        "../lock", "a/b.mbtiles", "..", ".", "..mbtiles", "x..y.mbtiles", "back\\slash.mbtiles", "%2e%2e%2flock", "",
    )

    private val contentSegments = listOf(
        listOf("fonts", "Roboto Bold", "0-255.pbf"), listOf("sprites", "day_simplified.json"), listOf("sprites", "day_simplified@2x.png"),
        listOf("..", "config", "application.json"), listOf("sprites", ".", "day_simplified.json"), listOf("sprites", "", "x.json"),
        listOf("fonts", "..", "sprites", "day_simplified.json"), emptyList(), listOf("upload.html"), listOf("fonts", "Roboto%20Bold", "0-255.pbf"),
    )

    /** Kotlin: `File(chartDir, fileName)` verbatim, written through a `.tmp` sibling and renamed. */
    private fun uploadOutcome(name: String): JsonElement {
        val save = File("/data/save")
        val dest = File(save, name)
        return buildJsonObject {
            put("status", 200)
            put("writes", relativeTo(save, dest))
            put("tmp", relativeTo(save, File(save, "$name.tmp")))
        }
    }

    /** D2: non-empty, no `/`, `\` or NUL, not `.` or `..`, ends in `.zip` case-insensitively. */
    private fun uploadPort(name: String): PortDivergence<JsonElement>? {
        val bare = name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && !name.contains('\u0000') &&
                name != "." && name != ".." && name.endsWith(".zip", ignoreCase = true) && name.length > 4
        val port = if (bare) buildJsonObject { put("status", 200); put("writes", name); put("tmp", "$name.tmp") }
        else buildJsonObject { put("status", 400) }
        return if (port == uploadOutcome(name)) null else PortDivergence("D2", port)
    }

    private val sandbox by lazy { TestEnv.tempDir("security_paths") }

    /** Really extract one entry and report what appeared on disk, relative to the extraction dir. */
    private fun zipOutcome(entry: String): JsonElement {
        val case = File(sandbox, "case_${zipEntries.indexOf(entry)}")
        val target = File(case, "x/y/target") // deep enough that `../..` stays inside the sandbox
        target.mkdirs()
        val src = File(case, "payload.txt").also { it.write("payload") }
        val zip = File(case, "entry.zip")
        ZipWriter.create(zip).use { w ->
            if (entry.endsWith("/")) w.addDirectory(entry) else w.addFile(entry, src)
        }
        val result = runCatching { ZipFile(zip).entries().forEach { it.unzipToPath(target) } }
        // unzipToPath writes File(target, name): report where that resolved to, relative to target
        val written = File(target, entry)
        val writes = when {
            entry.endsWith("/") && written.isDirectory() -> listOf(relativeTo(target, written) + "/")
            written.isFile() -> listOf(relativeTo(target, written))
            else -> emptyList()
        }
        return buildJsonObject {
            put("extracted", result.isSuccess)
            put("writes", kotlinx.serialization.json.JsonArray(writes.map { JsonPrimitive(it) }))
        }
    }

    /** D3: absolute, `..` component or backslash -> the whole extraction is rejected, nothing written. */
    private fun zipPort(entry: String): PortDivergence<JsonElement>? {
        val components = entry.split('/')
        val bad = entry.startsWith("/") || entry.contains('\\') || components.any { it == ".." }
        if (!bad) return null
        return PortDivergence("D3", buildJsonObject { put("extracted", false); put("writes", kotlinx.serialization.json.JsonArray(emptyList())) })
    }

    @Test
    fun paths() {
        val inputs = contentSegments.map { PathInput("content_resource", segments = it) } +
                archiveNames.map { PathInput("archive_name", name = it) } +
                uploadNames.map { PathInput("upload_filename", name = it) } +
                zipEntries.map { PathInput("zip_entry", name = it) }
        val port: (PathInput) -> PortDivergence<JsonElement>? = { input ->
            when (input.kind) {
                "upload_filename" -> uploadPort(input.name!!)
                "zip_entry" -> zipPort(input.name!!)
                else -> null
            }
        }
        val table = fixtures.table(
            "security/paths.json",
            FixtureHeader(
                pins = "caller-supplied names as paths: resourceName() refuses empty/./.. segments; RegionArchiveHandler refuses '/' and '..'; EncSaveHandler writes File(save, filename) verbatim (D2 port: bare .zip only, else 400); ZipFileEntry.unzipToPath writes wherever the entry name points (D3 port: reject absolute, .. and backslash entries)",
                decisions = listOf("D2", "D3", "SEC-3", "SEC-4", "SEC-7"),
                generator = "SecurityPathsFixtureTest.paths",
                source = "zip entries observed by extracting zips built with ZipWriter into a scratch directory",
            ),
            inputs,
            port,
        ) { input ->
            when (input.kind) {
                "content_resource" -> input.segments!!.resourceName()?.let { JsonPrimitive(it) } ?: JsonNull
                "archive_name" -> JsonPrimitive(!(input.name!!.contains('/') || input.name.contains("..")))
                "upload_filename" -> uploadOutcome(input.name!!)
                "zip_entry" -> zipOutcome(input.name!!)
                else -> error(input.kind)
            }
        }
        assertEquals(inputs.size, table.cases.size)
        // the headline D3 fact: a `../` entry really does land outside the extraction directory today
        val escape = zipOutcome("../escape.txt")
        assertEquals("""{"extracted":true,"writes":["../escape.txt"]}""", escape.toString())
        assertEquals("""{"status":200,"writes":"../escape.zip","tmp":"../escape.zip.tmp"}""", uploadOutcome("../escape.zip").toString())
    }
}
