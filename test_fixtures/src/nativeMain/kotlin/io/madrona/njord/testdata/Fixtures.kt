@file:OptIn(ExperimentalForeignApi::class)

package io.madrona.njord.testdata

import File
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import platform.posix.getenv

/**
 * How a test treats the fixture files under `testdata/`.
 *
 * Selected with the `NJORD_FIXTURES` environment variable: `regenerate` rewrites every fixture
 * the test suite touches from the current Kotlin implementation; anything else (the default)
 * verifies the implementation against the committed files and fails on any difference.
 */
enum class FixtureMode { VERIFY, REGENERATE }

/**
 * The header every fixture file carries (RUST_PORT_PLAN.md §8, "Fixtures freeze bugs as
 * behavior"): a fixture is only as trustworthy as the decision it records, so the decision
 * travels with the data.
 *
 * @property pins one or two sentences naming the behavior the file freezes
 * @property decisions the defect decisions (`D1`…`D9`) and audit IDs (`SY-3`, `TE-1`, …) the
 *   expected values depend on; an empty list is a claim that none apply
 * @property generator the Kotlin test that regenerates the file, e.g. `SoundgTest.displayDepths`
 * @property source where the inputs came from when they are not hand-written, e.g.
 *   `fixtures/US5WA22M/US5WA22M.000 (edition 40, update 4)`
 */
@Serializable
data class FixtureHeader(
    val pins: String,
    val decisions: List<String> = emptyList(),
    val generator: String,
    val source: String? = null,
)

/**
 * One `{ "input": …, "expected": … }` row of a table fixture.
 *
 * [port] is present only where the Kotlin result ([expected]) and the Rust port's required
 * result legitimately differ because of an accepted defect decision (TEST_COVERAGE_AUDIT.md
 * §3.1). Kotlin tests verify [expected]; the Rust tests assert `port.expected` when present.
 */
data class FixtureCase(val input: JsonElement, val expected: JsonElement, val port: JsonElement? = null)

/**
 * The corrected value the Rust port must produce for a case where Kotlin keeps an accepted
 * defect, and the decision (`D2`…`D9`) that says so.
 */
data class PortDivergence<E>(val decision: String, val expected: E)

/** A parsed table fixture. */
data class FixtureTable(val header: FixtureHeader, val cases: List<FixtureCase>)

/**
 * Reads and writes the golden fixtures under [root] in the given [mode].
 *
 * Tests normally use [FixtureStore.default], which finds the repo's `testdata/` directory and
 * takes the mode from `NJORD_FIXTURES`. Construct one directly to point tests at a scratch
 * directory.
 *
 * File conventions (also documented in `testdata/README.md`):
 * - a table fixture is one JSON object: the [FixtureHeader] fields followed by `"cases"`, a
 *   list of `{ "input": …, "expected": … }`;
 * - a binary fixture is `<name>.bin` plus a `<name>.json` sidecar holding the header, the byte
 *   count and a decoded view of the bytes so diffs stay readable.
 */
class FixtureStore(val root: File, val mode: FixtureMode) {

    val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = false
    }

    fun file(relativePath: String): File = File(root, relativePath)

    /** Reads a fixture file as text; fails with a regeneration hint when it is missing. */
    fun readText(relativePath: String): String {
        val f = file(relativePath)
        if (!f.exists()) missing(relativePath)
        return f.readContents()
    }

    /** Reads a fixture file's bytes; fails with a regeneration hint when it is missing. */
    fun readBytes(relativePath: String): ByteArray {
        val f = file(relativePath)
        if (!f.exists()) missing(relativePath)
        return f.readData()
    }

    /** Parses a committed table fixture without regenerating or verifying it. */
    fun readTable(relativePath: String): FixtureTable {
        val obj = json.parseToJsonElement(readText(relativePath)).jsonObject
        val header = json.decodeFromJsonElement(FixtureHeader.serializer(), JsonObject(obj.filterKeys { it != CASES }))
        val cases = (obj[CASES] ?: fixtureFail(relativePath, "has no \"$CASES\" list")).jsonArray.map {
            val c = it.jsonObject
            FixtureCase(
                input = c[INPUT] ?: fixtureFail(relativePath, "case without \"$INPUT\": $c"),
                expected = c[EXPECTED] ?: fixtureFail(relativePath, "case without \"$EXPECTED\": $c"),
                port = c[PORT],
            )
        }
        return FixtureTable(header, cases)
    }

    /**
     * A table fixture whose inputs are defined by the test and whose expected values are
     * produced by the Kotlin implementation through [compute].
     *
     * In [FixtureMode.REGENERATE] the file is (re)written. In [FixtureMode.VERIFY] the header,
     * the input list and every expected value must match the committed file; the first
     * mismatches are reported with their case index and input so the diff is actionable.
     *
     * @return the cases as written or verified, for any additional assertions the test wants
     */
    inline fun <reified I, reified E> table(
        relativePath: String,
        header: FixtureHeader,
        inputs: List<I>,
        noinline port: ((I) -> PortDivergence<E>?)? = null,
        noinline compute: (I) -> E,
    ): FixtureTable = table(relativePath, header, inputs, serializer<I>(), serializer<E>(), port, compute)

    /**
     * @param port for an input where the Kotlin result is an accepted defect, the decision ID and
     *   the value the port must produce instead; null for every other input
     */
    fun <I, E> table(
        relativePath: String,
        header: FixtureHeader,
        inputs: List<I>,
        inputSerializer: KSerializer<I>,
        expectedSerializer: KSerializer<E>,
        port: ((I) -> PortDivergence<E>?)? = null,
        compute: (I) -> E,
    ): FixtureTable {
        val produced = inputs.map { input ->
            val divergence = port?.invoke(input)
            if (divergence != null) {
                require(divergence.decision in header.decisions) {
                    "fixture $relativePath: case diverges under ${divergence.decision} but the header does not list it in decisions"
                }
            }
            FixtureCase(
                input = json.encodeToJsonElement(inputSerializer, input),
                expected = json.encodeToJsonElement(expectedSerializer, compute(input)),
                port = divergence?.let {
                    buildJsonObject {
                        put(DECISION, it.decision)
                        put(EXPECTED, json.encodeToJsonElement(expectedSerializer, it.expected))
                    }
                },
            )
        }
        val table = FixtureTable(header, produced)
        when (mode) {
            FixtureMode.REGENERATE -> writeText(relativePath, json.encodeToString(JsonObject.serializer(), table.toJson()))
            FixtureMode.VERIFY -> verifyTable(relativePath, table)
        }
        return table
    }

    /**
     * A binary fixture: [bytes] are stored at `relativePath` (conventionally `*.bin`) and
     * [decoded], a readable view of the same bytes, goes into the `.json` sidecar with the header.
     *
     * Verification requires byte equality: the Kotlin implementation must be byte-stable with
     * itself. The Rust port compares the decoded view (RUST_PORT_PLAN.md §5.2, P-TE-2).
     */
    fun binary(relativePath: String, header: FixtureHeader, bytes: ByteArray, decoded: JsonElement) {
        val sidecarPath = sidecarOf(relativePath)
        val sidecar = buildJsonObject {
            header.toJson().forEach { (k, v) -> put(k, v) }
            put(BIN, file(relativePath).name)
            put(BYTES, bytes.size)
            put(DECODED, decoded)
        }
        when (mode) {
            FixtureMode.REGENERATE -> {
                writeBytes(relativePath, bytes)
                writeText(sidecarPath, json.encodeToString(JsonObject.serializer(), sidecar))
            }

            FixtureMode.VERIFY -> {
                val stored = readBytes(relativePath)
                val storedSidecar = json.parseToJsonElement(readText(sidecarPath)).jsonObject
                val problems = mutableListOf<String>()
                if (!stored.contentEquals(bytes)) {
                    val firstDiff = stored.indices.firstOrNull { it >= bytes.size || stored[it] != bytes[it] }
                        ?: stored.size
                    problems += "bytes differ: committed ${stored.size} bytes, produced ${bytes.size}, first difference at offset $firstDiff"
                }
                if (storedSidecar[DECODED] != decoded) {
                    problems += "decoded view differs\n  committed: ${storedSidecar[DECODED]}\n  produced:  $decoded"
                }
                val storedHeader = JsonObject(storedSidecar.filterKeys { it != BIN && it != BYTES && it != DECODED })
                if (storedHeader != header.toJson()) {
                    problems += "header differs\n  committed: $storedHeader\n  produced:  ${header.toJson()}"
                }
                if (problems.isNotEmpty()) fixtureFail(relativePath, problems.joinToString("\n"))
            }
        }
    }

    private fun verifyTable(relativePath: String, produced: FixtureTable) {
        val committed = readTable(relativePath)
        val problems = mutableListOf<String>()
        if (committed.header != produced.header) {
            problems += "header differs\n  committed: ${committed.header}\n  produced:  ${produced.header}"
        }
        if (committed.cases.size != produced.cases.size) {
            problems += "case count differs: committed ${committed.cases.size}, produced ${produced.cases.size}"
        }
        committed.cases.zip(produced.cases).forEachIndexed { i, (c, p) ->
            if (c.input != p.input) {
                problems += "case #$i input differs\n  committed: ${c.input}\n  produced:  ${p.input}"
            } else if (c.expected != p.expected) {
                problems += "case #$i (input ${c.input}) expected differs\n  committed: ${c.expected}\n  produced:  ${p.expected}"
            } else if (c.port != p.port) {
                problems += "case #$i (input ${c.input}) port divergence differs\n  committed: ${c.port}\n  produced:  ${p.port}"
            }
        }
        if (problems.isNotEmpty()) {
            val shown = problems.take(MAX_REPORTED)
            val more = if (problems.size > shown.size) "\n… and ${problems.size - shown.size} more" else ""
            fixtureFail(relativePath, shown.joinToString("\n") + more)
        }
    }

    /** `geo/mvt/case.bin` -> `geo/mvt/case.json` */
    private fun sidecarOf(relativePath: String): String = relativePath.substringBeforeLast('.') + ".json"

    private fun writeText(relativePath: String, text: String) = writeBytes(relativePath, text.encodeToByteArray())

    private fun writeBytes(relativePath: String, bytes: ByteArray) {
        val f = file(relativePath)
        f.parentFile()?.mkdirs()
        f.writeBytes(bytes)
        check(f.exists() && f.size() == bytes.size.toLong()) { "failed to write fixture $f" }
    }

    private fun missing(relativePath: String): Nothing =
        fixtureFail(relativePath, "does not exist under $root")

    private fun fixtureFail(relativePath: String, detail: String): Nothing =
        throw AssertionError(
            "fixture $relativePath $detail\n" +
                    "If the new behavior is intended, regenerate with NJORD_FIXTURES=regenerate " +
                    "and review the diff of testdata/ before committing."
        )

    private fun FixtureHeader.toJson(): JsonObject = json.encodeToJsonElement(this).jsonObject

    private fun FixtureTable.toJson(): JsonObject = buildJsonObject {
        header.toJson().forEach { (k, v) -> put(k, v) }
        put(CASES, JsonArray(cases.map { c ->
            buildJsonObject {
                put(INPUT, c.input)
                put(EXPECTED, c.expected)
                c.port?.let { put(PORT, it) }
            }
        }))
    }

    companion object {
        const val ENV_MODE = "NJORD_FIXTURES"
        const val ENV_ROOT = "NJORD_TESTDATA"
        const val MODE_REGENERATE = "regenerate"
        private const val MARKER = "README.md"
        private const val CASES = "cases"
        private const val INPUT = "input"
        private const val EXPECTED = "expected"
        private const val PORT = "port"
        private const val DECISION = "decision"
        private const val BIN = "bin"
        private const val BYTES = "bytes"
        private const val DECODED = "decoded"
        private const val MAX_REPORTED = 10

        /** The repo's `testdata/`, in the mode `NJORD_FIXTURES` selects. */
        val default: FixtureStore by lazy { FixtureStore(locateRoot(), modeFromEnv()) }

        fun modeFromEnv(): FixtureMode =
            if (getenv(ENV_MODE)?.toKString() == MODE_REGENERATE) FixtureMode.REGENERATE else FixtureMode.VERIFY

        /**
         * `NJORD_TESTDATA` when set, otherwise the nearest `testdata/README.md` walking up from
         * the working directory. Gradle runs native tests with the module directory as cwd, so
         * the repo root is one level up.
         */
        fun locateRoot(): File {
            getenv(ENV_ROOT)?.toKString()?.takeIf { it.isNotBlank() }?.let { return File(it) }
            var dir: File? = File.getCurrentDirectory()
            repeat(8) {
                val candidate = File(dir ?: return@repeat, "testdata")
                if (File(candidate, MARKER).exists()) return candidate
                dir = dir?.getAbsolutePath()?.let { File(it) }?.parentFile()
            }
            error("cannot find testdata/$MARKER above ${File.getCurrentDirectory()}; set $ENV_ROOT")
        }
    }
}

/** Shorthand for [FixtureStore.default]. */
val fixtures: FixtureStore get() = FixtureStore.default
