import io.madrona.njord.db.Dao
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.PortDivergence
import io.madrona.njord.testdata.fixtures
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P-DB-7: [Dao.sqlOpAsync]'s retry policy as a spec table (`testdata/db/retry.json`), run against
 * a fake [DataSource] under virtual time so the backoff is pinned exactly.
 *
 * Kotlin re-runs the block after *any* failure, which is defect D5 (a chart insert that threw
 * after writing rows is replayed, and the report counts every attempt). The `port` field carries
 * the D5 policy: retry only while no connection can be acquired; a block that threw is never
 * re-run. The null-connection and cancellation rows are identical on both sides.
 */
class DaoRetryFixtureTest {

    @BeforeTest
    fun before() = TestEnv.init()

    @Serializable
    data class RetryInput(
        /** how many `connection()` calls return null before one succeeds (`-1` = always null) */
        val nullConnections: Int,
        /** how many block invocations throw before one succeeds (`-1` = always throws) */
        val blockFailures: Int,
        /** the block throws CancellationException on its first invocation */
        val cancel: Boolean = false,
        /** the `tryCount` argument, or null for the default of 7 */
        val tryCount: Int? = null,
    )

    @Serializable
    data class RetryOutcome(
        val result: String?,
        val connectionAttempts: Int,
        val blockInvocations: Int,
        val totalDelayMs: Long,
    )

    private class FakeConnection : Connection {
        override fun statement(sql: String): Statement = error("not a real connection")
        override fun prepareStatement(sql: String): Statement = error("not a real connection")
        override fun prepareStatement(sql: String, identifier: Int): Statement = error("not a real connection")
        override fun close() {}
    }

    private class FakeDataSource(private val nullConnections: Int) : DataSource {
        var attempts = 0
        override suspend fun connection(): Connection? {
            attempts++
            return if (nullConnections < 0 || attempts <= nullConnections) null else FakeConnection()
        }
    }

    private class ProbeDao(ds: DataSource) : Dao(ds) {
        suspend fun <T> op(tryCount: Int?, block: suspend (Connection) -> T): T? =
            if (tryCount == null) sqlOpAsync(block = block) else sqlOpAsync(tryCount = tryCount, block = block)
    }

    private fun run(input: RetryInput): RetryOutcome {
        val ds = FakeDataSource(input.nullConnections)
        var invocations = 0
        var result: String? = null
        var delay = 0L
        runTest {
            result = try {
                ProbeDao(ds).op(input.tryCount) {
                    invocations++
                    if (input.cancel && invocations == 1) throw CancellationException("cancelled")
                    if (input.blockFailures < 0 || invocations <= input.blockFailures) throw IllegalStateException("boom $invocations")
                    "ok after $invocations"
                }
            } catch (e: CancellationException) {
                "cancellation propagated"
            }
            delay = testScheduler.currentTime
        }
        return RetryOutcome(result, ds.attempts, invocations, delay)
    }

    /** The D5 policy: a thrown block is final on the port; connection failures retry as in Kotlin. */
    private fun port(input: RetryInput): PortDivergence<RetryOutcome>? {
        if (input.blockFailures == 0 || input.cancel) return null
        val max = input.tryCount ?: Dao.MAX_TRY_COUNT
        val connectionAttempts = if (input.nullConnections < 0) max else minOf(input.nullConnections, max) + 1
        if (input.nullConnections < 0 || input.nullConnections >= max) return null // never reaches the block either way
        val delayBeforeBlock = (1..input.nullConnections).sumOf { 100L * it }
        return PortDivergence("D5", RetryOutcome(null, connectionAttempts, 1, delayBeforeBlock))
    }

    @Test
    fun retryTable() {
        val inputs = listOf(
            RetryInput(0, 0),
            RetryInput(1, 0), RetryInput(3, 0), RetryInput(6, 0), RetryInput(7, 0), RetryInput(-1, 0),
            RetryInput(0, 1), RetryInput(0, 3), RetryInput(0, 6), RetryInput(0, 7), RetryInput(0, -1),
            RetryInput(2, 2), RetryInput(3, 4),
            RetryInput(0, 0, cancel = true), RetryInput(2, 0, cancel = true),
            RetryInput(0, 0, tryCount = 2), RetryInput(1, 0, tryCount = 2), RetryInput(2, 0, tryCount = 2),
            RetryInput(0, 1, tryCount = 2), RetryInput(0, 2, tryCount = 2), RetryInput(0, -1, tryCount = 1),
        )
        val table = fixtures.table(
            "db/retry.json",
            FixtureHeader(
                pins = "Dao.sqlOpAsync: up to tryCount (default 7) attempts; a null connection or a thrown block retries after 100 ms × attempt; CancellationException is never retried and is swallowed to null; null when attempts run out",
                decisions = listOf("D5", "DB-13"),
                generator = "DaoRetryFixtureTest.retryTable",
                source = "virtual time (kotlinx-coroutines-test), fake DataSource",
            ),
            inputs,
            ::port,
        ) { run(it) }

        // the headline facts, stated plainly
        assertEquals(RetryOutcome("ok after 1", 1, 1, 0), run(RetryInput(0, 0)))
        assertEquals(RetryOutcome(null, 7, 0, 2100), run(RetryInput(-1, 0)), "7 null connections, backoff 100+200+…+600")
        assertEquals(RetryOutcome(null, 7, 7, 2100), run(RetryInput(0, -1)), "a block that always throws is tried 7 times (D5)")
        // runCatching swallows the CancellationException: not retried, but also not propagated - the caller sees null
        assertEquals(RetryOutcome(null, 1, 1, 0), run(RetryInput(0, 0, cancel = true)))
        assertEquals(inputs.size, table.cases.size)
    }
}
