import io.madrona.njord.Singletons
import io.madrona.njord.endpoints.AdminUtil
import io.madrona.njord.endpoints.HmacSHA256
import io.madrona.njord.model.AdminSignature
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.PortDivergence
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.http.decodeURLPart
import kotlin.io.encoding.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * P-SEC-1 / P-SEC-2: the admin signature scheme. A signature is HMAC-SHA256(adminKey) over
 * `baseUrl ‖ date.toString() ‖ uuid`, base64; the encoded form is base64 of the signature JSON,
 * URL-path-encoded. Signatures minted by the Kotlin server during cutover must verify on the Rust
 * port and vice versa, so `hmac_vectors.json` pins the primitive and the exact strings, and
 * `verify_cases.json` pins every accept/reject decision - including the D4 divergence, where
 * Kotlin throws on malformed input and the port answers `false`.
 */
class AdminSignatureFixtureTest {

    @BeforeTest
    fun before() = TestEnv.init()

    private val key = "test-key"
    private val baseUrl = "https://njord.test"
    private val util by lazy { AdminUtil(Singletons.config.copy(adminKey = key, adminExpirationSeconds = 600)) }
    private val now = Instant.parse("2026-01-02T03:04:05Z")
    private val uuid = "AbCdEfGh-IjKl-MnOp-QrSt-UvWxYz012345"

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Serializable
    data class HmacInput(val key: String, val data: List<String>)

    @Serializable
    data class SignInput(val baseUrl: String, val now: String, val uuid: String, val adminKey: String, val expirationSeconds: Long)

    @Serializable
    data class Minted(val date: String, val signature: String, val uuid: String, val expirationDate: String, val signatureEncoded: String)

    /**
     * RFC 4231 test cases whose keys are valid UTF-8 (the constructor takes a String), plus the
     * signature inputs. An empty key is not a case: `HmacSHA256("")` crashes on `addressOf(0)` of an
     * empty array today, and the admin key is never empty in practice.
     */
    @Test
    fun hmacVectors() {
        val tc1Key = CharArray(20) { 0x0b.toChar() }.concatToString()
        val vectors = listOf(
            HmacInput(tc1Key, listOf("Hi There")),
            HmacInput("Jefe", listOf("what do ya want for nothing?")),
            HmacInput("Jefe", listOf("what do ya want ", "for nothing?")),
            HmacInput("key", listOf("The quick brown fox jumps over the lazy dog")),
            HmacInput(key, listOf(baseUrl, now.toString(), uuid)),
        )
        val table = fixtures.table(
            "admin/hmac_vectors.json",
            FixtureHeader(
                pins = "HmacSHA256(key).update(each data chunk).doFinal() as lowercase hex; RFC 4231 TC1/TC2 included; chunking does not change the digest",
                decisions = listOf("SEC-2"),
                generator = "AdminSignatureFixtureTest.hmacVectors",
            ),
            vectors,
        ) { input -> HmacSHA256(input.key).also { h -> input.data.forEach { h.update(it.encodeToByteArray()) } }.doFinal().let(::hex) }
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", table.cases[0].expected.let { (it as JsonPrimitive).content })
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", table.cases[1].expected.let { (it as JsonPrimitive).content })
        assertEquals(table.cases[1].expected, table.cases[2].expected)

        val signs = listOf(
            SignInput(baseUrl, "2026-01-02T03:04:05Z", uuid, key, 600),
            SignInput(baseUrl, "2026-01-02T03:04:05.123Z", uuid, key, 600),
            SignInput(baseUrl, "2026-01-02T03:04:05.123456789Z", uuid, key, 600),
            SignInput(baseUrl, "2026-01-02T03:04:05.100Z", uuid, key, 604800),
            SignInput("http://localhost:9000", "2026-01-02T03:04:05Z", uuid, key, 600),
            SignInput("https://openenc.com", "2026-06-30T23:59:59Z", "zyxwvuts-rqpo-nmlk-jihg-fedcba987654", "changeme", 604800),
        )
        fixtures.table(
            "admin/signature_vectors.json",
            FixtureHeader(
                pins = "AdminUtil.createSignatureResponse with a fixed clock and nonce: date re-serialised as kotlinx Instant.toString (RFC 3339 Z, fractional seconds trimmed in groups of three), HMAC over baseUrl+date+uuid base64, expirationDate = date + adminExpirationSeconds, signatureEncoded = URL-path-encoded base64 of the signature JSON",
                decisions = listOf("SEC-1", "D4"),
                generator = "AdminSignatureFixtureTest.hmacVectors",
            ),
            signs,
        ) { input ->
            val u = AdminUtil(Singletons.config.copy(adminKey = input.adminKey, adminExpirationSeconds = input.expirationSeconds))
            val r = u.createSignatureResponse(input.baseUrl, Instant.parse(input.now), input.uuid)
            Minted(r.signature.date.toString(), r.signature.signature, r.signature.uuid, r.signature.expirationDate.toString(), r.signatureEncoded)
        }
    }

    @Serializable
    data class VerifyInput(
        val description: String,
        val baseUrl: String,
        val now: String,
        val adminKey: String,
        val signature: AdminSignature? = null,
        val query: String? = null,
    )

    private fun verify(input: VerifyInput): JsonElement = try {
        val u = AdminUtil(Singletons.config.copy(adminKey = input.adminKey, adminExpirationSeconds = 600))
        val at = Instant.parse(input.now)
        val ok = input.query?.let { u.verifySignature(it, input.baseUrl, at) } ?: u.verifySignature(input.signature!!, input.baseUrl, at)
        JsonPrimitive(ok)
    } catch (t: Throwable) {
        buildJsonObject { put("throws", t::class.simpleName ?: "Throwable") }
    }

    private fun encodedQuery(text: String) = Base64.encode(text.encodeToByteArray())

    @Test
    fun verifyCases() {
        val minted = util.createSignatureResponse(baseUrl, now, uuid)
        val sig = minted.signature
        fun at(seconds: Long) = (now + seconds.seconds).toString()
        val cases = listOf(
            VerifyInput("fresh", baseUrl, at(10), key, sig),
            VerifyInput("exactly at expiry (elapsed == adminExpirationSeconds passes)", baseUrl, at(600), key, sig),
            VerifyInput("one second past expiry", baseUrl, at(601), key, sig),
            VerifyInput("far past expiry", baseUrl, at(7 * 24 * 3600), key, sig),
            VerifyInput("future-dated signature (HMAC covers the date, expiry only checks age)", baseUrl, at(-100), key, sig),
            VerifyInput("expirationDate is informational and never checked", baseUrl, at(10), key, sig.copy(expirationDate = now - 1.seconds)),
            VerifyInput("tampered uuid", baseUrl, at(10), key, sig.copy(uuid = uuid.replaceFirstChar { 'Z' })),
            VerifyInput("tampered date", baseUrl, at(10), key, sig.copy(date = now + 1.seconds)),
            VerifyInput("tampered signature byte", baseUrl, at(10), key, sig.copy(signature = sig.signature.replaceFirstChar { if (it == 'A') 'B' else 'A' })),
            VerifyInput("signature not base64", baseUrl, at(10), key, sig.copy(signature = "not base64!")),
            VerifyInput("signature decodes to the wrong length", baseUrl, at(10), key, sig.copy(signature = Base64.encode(ByteArray(16)))),
            VerifyInput("different base url (scheme)", "http://njord.test", at(10), key, sig),
            VerifyInput("different base url (port)", "$baseUrl:8443", at(10), key, sig),
            VerifyInput("different admin key", baseUrl, at(10), "other-key", sig),
            VerifyInput("encoded query, fresh", baseUrl, at(10), key, query = minted.signatureEncoded),
            VerifyInput("encoded query, expired", baseUrl, at(601), key, query = minted.signatureEncoded),
            VerifyInput("encoded query, not base64", baseUrl, at(10), key, query = "not-base64!"),
            VerifyInput("encoded query, base64 of non-JSON", baseUrl, at(10), key, query = encodedQuery("hello")),
            VerifyInput("encoded query, JSON missing a field", baseUrl, at(10), key, query = encodedQuery("""{"date":"2026-01-02T03:04:05Z","uuid":"x"}""")),
            VerifyInput("encoded query, unparsable date", baseUrl, at(10), key, query = encodedQuery("""{"date":"yesterday","signature":"x","uuid":"x","expirationDate":"2026-01-02T03:04:05Z"}""")),
            VerifyInput("encoded query, empty", baseUrl, at(10), key, query = ""),
        )
        val port: (VerifyInput) -> PortDivergence<JsonElement>? = { input ->
            val kotlin = verify(input)
            if (kotlin is JsonPrimitive) null else PortDivergence("D4", JsonPrimitive(false))
        }
        val table = fixtures.table(
            "admin/verify_cases.json",
            FixtureHeader(
                pins = "AdminUtil.verifySignature: HMAC over baseUrl+date+uuid must match the presented base64 text, rejected only when date.until(now) exceeds adminExpirationSeconds (600 here); expirationDate unchecked; malformed encoded input throws in Kotlin (port: false, constant-time compare)",
                decisions = listOf("D4", "SEC-1"),
                generator = "AdminSignatureFixtureTest.verifyCases",
                source = "a signature minted at ${now} for $baseUrl with key \"$key\"",
            ),
            cases,
            port,
        ) { verify(it) }

        val byName = table.cases.associate { (it.input as kotlinx.serialization.json.JsonObject)["description"]!!.let { d -> (d as JsonPrimitive).content } to it.expected }
        assertEquals(JsonPrimitive(true), byName["fresh"])
        assertEquals(JsonPrimitive(true), byName["exactly at expiry (elapsed == adminExpirationSeconds passes)"])
        assertEquals(JsonPrimitive(false), byName["one second past expiry"])
        assertEquals(JsonPrimitive(true), byName["future-dated signature (HMAC covers the date, expiry only checks age)"])
        assertEquals(JsonPrimitive(false), byName["tampered uuid"])
        assertEquals(JsonPrimitive(true), byName["encoded query, fresh"])
        assertTrue(byName["encoded query, not base64"] !is JsonPrimitive, "Kotlin throws on malformed input (D4)")
        assertFalse(util.verifySignature(sig, baseUrl, now + 601.seconds))
        // the JSON the encoded query carries is the AdminSignature document
        val decoded = Base64.decode(minted.signatureEncoded.decodeURLPart()).decodeToString()
        assertEquals(sig, Json.decodeFromString(AdminSignature.serializer(), decoded))
    }
}
