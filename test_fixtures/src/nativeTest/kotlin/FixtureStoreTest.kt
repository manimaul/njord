import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.FixtureMode
import io.madrona.njord.testdata.FixtureStore
import io.madrona.njord.testdata.PortDivergence
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixtureStoreTest {

    @Serializable
    data class Pair(val a: Int, val b: Int)

    private val scratch = File("./build/tmp/fixture_store_test")
    private val header = FixtureHeader(
        pins = "integer addition",
        decisions = listOf("D0"),
        generator = "FixtureStoreTest",
    )
    private val inputs = listOf(Pair(1, 2), Pair(-5, 5), Pair(0, 0))

    private fun store(mode: FixtureMode) = FixtureStore(scratch, mode)

    @BeforeTest
    fun setUp() {
        scratch.deleteRecursively()
        scratch.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        scratch.deleteRecursively()
    }

    @Test
    fun `regenerate writes a table with header and cases and verify accepts it`() {
        val written = store(FixtureMode.REGENERATE).table("math/add.json", header, inputs) { it.a + it.b }
        assertEquals(3, written.cases.size)
        assertEquals(JsonPrimitive(3), written.cases[0].expected)

        val text = scratch.let { File(it, "math/add.json") }.readContents()
        assertTrue(text.contains("\"pins\": \"integer addition\""), text)
        assertTrue(text.contains("\"cases\": ["), text)

        val verified = store(FixtureMode.VERIFY).table("math/add.json", header, inputs) { it.a + it.b }
        assertEquals(written, verified)
        assertEquals(header, store(FixtureMode.VERIFY).readTable("math/add.json").header)
    }

    @Test
    fun `verify fails naming the case when the implementation changes`() {
        store(FixtureMode.REGENERATE).table("math/add.json", header, inputs) { it.a + it.b }
        val ex = assertFailsWith<AssertionError> {
            store(FixtureMode.VERIFY).table("math/add.json", header, inputs) { it.a - it.b }
        }
        val msg = ex.message!!
        assertTrue(msg.contains("case #0"), msg)
        assertTrue(msg.contains("committed: 3"), msg)
        assertTrue(msg.contains("produced:  -1"), msg)
        assertTrue(msg.contains(FixtureStore.ENV_MODE), msg)
    }

    @Test
    fun `verify fails when the inputs or the header change`() {
        store(FixtureMode.REGENERATE).table("math/add.json", header, inputs) { it.a + it.b }
        assertFailsWith<AssertionError> {
            store(FixtureMode.VERIFY).table("math/add.json", header, inputs + Pair(9, 9)) { it.a + it.b }
        }.also { assertTrue(it.message!!.contains("case count differs"), it.message) }
        assertFailsWith<AssertionError> {
            store(FixtureMode.VERIFY).table("math/add.json", header.copy(pins = "something else"), inputs) { it.a + it.b }
        }.also { assertTrue(it.message!!.contains("header differs"), it.message) }
    }

    @Test
    fun `a port divergence is written next to the Kotlin value and verified`() {
        val divergent = header.copy(decisions = listOf("D0"))
        val port: (Pair) -> PortDivergence<Int>? = { if (it.a < 0) PortDivergence("D0", 0) else null }
        val written = store(FixtureMode.REGENERATE).table("math/add.json", divergent, inputs, port) { it.a + it.b }
        assertEquals(null, written.cases[0].port)
        val text = File(scratch, "math/add.json").readContents()
        assertTrue(text.contains("\"port\": {"), text)
        assertTrue(text.contains("\"decision\": \"D0\""), text)

        val read = store(FixtureMode.VERIFY).readTable("math/add.json")
        assertEquals(JsonPrimitive(0), (read.cases[1].port as kotlinx.serialization.json.JsonObject)["expected"])
        store(FixtureMode.VERIFY).table("math/add.json", divergent, inputs, port) { it.a + it.b }

        val ex = assertFailsWith<AssertionError> {
            store(FixtureMode.VERIFY).table("math/add.json", divergent, inputs) { it.a + it.b }
        }
        assertTrue(ex.message!!.contains("port divergence differs"), ex.message)

        assertFailsWith<IllegalArgumentException> {
            store(FixtureMode.REGENERATE).table("math/add.json", header.copy(decisions = emptyList()), inputs, port) { it.a + it.b }
        }.also { assertTrue(it.message!!.contains("does not list it in decisions"), it.message) }
    }

    @Test
    fun `verify of a missing fixture tells how to regenerate`() {
        val ex = assertFailsWith<AssertionError> {
            store(FixtureMode.VERIFY).table("math/nope.json", header, inputs) { it.a + it.b }
        }
        assertTrue(ex.message!!.contains("does not exist"), ex.message)
        assertTrue(ex.message!!.contains("NJORD_FIXTURES=regenerate"), ex.message)
    }

    @Test
    fun `binary fixtures write bin plus sidecar and verify byte for byte`() {
        val bytes = byteArrayOf(0x0a, 0x00, 0x7f, -1)
        val decoded = buildJsonObject { put("layers", 1) }
        store(FixtureMode.REGENERATE).binary("geo/mvt/case.bin", header, bytes, decoded)
        assertTrue(File(scratch, "geo/mvt/case.bin").exists())
        val sidecar = store(FixtureMode.VERIFY).json.parseToJsonElement(
            File(scratch, "geo/mvt/case.json").readContents()
        )
        assertEquals("case.bin", sidecar.let { (it as kotlinx.serialization.json.JsonObject)["bin"]!!.jsonPrimitive.content })

        store(FixtureMode.VERIFY).binary("geo/mvt/case.bin", header, bytes, decoded)
        val ex = assertFailsWith<AssertionError> {
            store(FixtureMode.VERIFY).binary("geo/mvt/case.bin", header, byteArrayOf(0x0a, 0x01, 0x7f, -1), decoded)
        }
        assertTrue(ex.message!!.contains("first difference at offset 1"), ex.message)
    }

    @Test
    fun `default store finds the repo testdata directory`() {
        val root = FixtureStore.locateRoot()
        assertTrue(File(root, "README.md").exists(), "no README.md in $root")
        assertTrue(File(root, "fixtures/US5WA22M/US5WA22M.000").exists(), "no NOAA cell in $root")
        assertTrue(File(root, "fixtures/synthetic/US4ZZ01M.000").exists(), "no synthetic cell in $root")
        assertEquals(FixtureMode.VERIFY, FixtureStore.default.mode, "tests must not run in regenerate mode by accident")
    }
}
