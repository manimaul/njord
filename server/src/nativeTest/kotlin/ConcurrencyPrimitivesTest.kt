import io.madrona.njord.ingest.ChartIngestWorker
import io.madrona.njord.ingest.IngestStatus
import io.madrona.njord.ingest.RegionExportWorker
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.PortDivergence
import io.madrona.njord.testdata.fixtures
import io.madrona.njord.util.DistributedLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * P-CC-1 … P-CC-3: the inter-process protocol between replicas - the lock file, the status file
 * and the worker cadence. These are behavioural tests of the Kotlin semantics; the decisions the
 * port must honour (D6) are written down in `testdata/concurrency/lock_semantics.json` and
 * `cadence.json` as small decision tables, since a race cannot be golden-filed.
 */
class ConcurrencyPrimitivesTest {

    @BeforeTest
    fun before() = TestEnv.init()

    private fun lockFile(name: String) = File(TestEnv.tempDir("lock_$name"), "lock")

    // ---- P-CC-1 DistributedLock -------------------------------------------------------------

    @Test
    fun `the lock file holds the owner uuid as text and an empty file means unlocked`() {
        val file = lockFile("format")
        val lock = DistributedLock(lockFile = file)
        assertFalse(file.exists())
        assertTrue(lock.tryAcquireLock())
        assertEquals(lock.uuid, file.readContents())
        assertTrue(lock.lockAcquired)
        assertTrue(lock.tryClearLock())
        assertTrue(file.exists(), "clearing leaves an empty file, not a missing one")
        assertEquals("", file.readContents())
        assertTrue(file.isEmpty())
        // no temp files left behind by the rename protocol
        assertEquals(listOf("lock"), file.parentFile()!!.listFiles(false).map { it.name })
    }

    @Test
    fun `a second instance cannot acquire a held lock and a stale uuid is never ours`() {
        val file = lockFile("held")
        val first = DistributedLock(lockFile = file)
        val second = DistributedLock(lockFile = file)
        assertTrue(first.tryAcquireLock())
        assertFalse(second.tryAcquireLock())
        assertEquals(first.uuid, file.readContents(), "a failed acquire does not overwrite the holder")
        // a lock file left by a dead process is honoured as held
        val stale = lockFile("stale").also { it.write("some-other-process-uuid") }
        assertFalse(DistributedLock(lockFile = stale).tryAcquireLock())
    }

    @Test
    fun `a non-owner may clear the lock as the operator abort`() {
        val file = lockFile("clear")
        val owner = DistributedLock(lockFile = file)
        val other = DistributedLock(lockFile = file)
        assertTrue(owner.tryAcquireLock())
        assertTrue(other.tryClearLock())
        assertEquals("", file.readContents())
        assertTrue(other.tryAcquireLock())
        assertFalse(owner.tryAcquireLock())
    }

    /**
     * CC-1: N instances racing on one file. Acquire is check-then-rename, so more than one may
     * believe it won for an instant (that is D6); what must hold is that the file ends up naming
     * exactly one of them and only that one still reads as holder afterwards.
     */
    @Test
    fun `contention settles on exactly one holder`() {
        val file = lockFile("race")
        val instances = (1..8).map { DistributedLock(lockFile = file) }
        val claimed = runBlocking {
            instances.map { lock -> async(Dispatchers.Default) { lock.tryAcquireLock() } }.awaitAll()
        }
        assertTrue(claimed.any { it }, "somebody must have acquired")
        val holder = file.readContents()
        val holders = instances.filter { it.uuid == holder }
        assertEquals(1, holders.size, "the file names exactly one instance")
        assertFalse(instances.filter { it !== holders.single() }.any { it.tryAcquireLock() }, "nobody else can take it now")
    }

    @Serializable
    data class SemanticsInput(val question: String)

    @Test
    fun lockSemantics() {
        val kotlin = mapOf(
            "lock file content while held" to JsonPrimitive("owner uuid as text"),
            "lock file content while free" to JsonPrimitive("empty file (or missing)"),
            "acquire" to JsonPrimitive("if the file is empty: write uuid to lock.<random> and rename it over lock; acquired = file now reads own uuid"),
            "acquire settle re-read delay ms" to JsonPrimitive(0),
            "clear" to JsonPrimitive("touch lock.<random> (empty) and rename it over lock"),
            "non-owner may clear" to JsonPrimitive(true),
            "POST /v1/enc_save clears the lock (aborts an in-flight ingest)" to JsonPrimitive(true),
            "DELETE /v1/enc_save clears the lock" to JsonPrimitive(true),
            "ingest aborts when lockAcquired turns false" to JsonPrimitive(true),
            "lock watch" to JsonPrimitive("inotify on the lock directory; 1000 ms poll fallback when inotify is unavailable"),
        )
        val port = mapOf(
            "acquire settle re-read delay ms" to JsonPrimitive(1500),
            "POST /v1/enc_save clears the lock (aborts an in-flight ingest)" to JsonPrimitive(false),
        )
        fixtures.table(
            "concurrency/lock_semantics.json",
            FixtureHeader(
                pins = "DistributedLock protocol as the Kotlin server implements it; the port keeps the file protocol (a Kotlin-provisioned lock dir is upgraded in place) and changes only what D6 lists",
                decisions = listOf("D6", "CC-1", "CC-2"),
                generator = "ConcurrencyPrimitivesTest.lockSemantics",
            ),
            kotlin.keys.map { SemanticsInput(it) },
            { input: SemanticsInput -> port[input.question]?.let { PortDivergence<JsonElement>("D6", it) } },
        ) { kotlin[it.question]!! }
    }

    // ---- P-CC-2 IngestStatus -------------------------------------------------------------------

    private fun statusEnv(name: String): Triple<File, DistributedLock, IngestStatus> {
        val dir = TestEnv.tempDir("status_$name")
        val lock = DistributedLock(lockFile = File(dir, "lock"))
        val status = IngestStatus(statusFile = File(dir, "status.json"), distributedLock = lock)
        return Triple(dir, lock, status)
    }

    private fun WsMsg.json() = Json.encodeToString(WsMsg.serializer(), this)

    /** Polls [DistributedLock.lockAcquired] for up to two seconds - the fallback watch samples the file once a second (CC-3). */
    private fun awaitLockState(lock: DistributedLock, acquired: Boolean) {
        val deadline = kotlin.time.TimeSource.Monotonic.markNow() + 3.seconds
        while (lock.lockAcquired != acquired && deadline.hasNotPassedNow()) runBlocking { delay(50) }
        assertEquals(acquired, lock.lockAcquired, "lockAcquired should have become $acquired within the watch period")
    }

    @Test
    fun `writeMsg only writes while holding the lock and writeMsgIgnoreLock always writes`() {
        val (dir, lock, status) = statusEnv("lock")
        val file = File(dir, "status.json")
        status.writeMsg(WsMsg.Extracting(0.5f))
        assertFalse(file.exists(), "no lock, no write")
        status.writeMsgIgnoreLock(WsMsg.Idle)
        assertEquals(WsMsg.Idle.json(), file.readContents())
        assertTrue(lock.tryAcquireLock())
        val info = WsMsg.Info(1, 2, 0, 1)
        status.writeMsg(info)
        assertEquals(info.json(), file.readContents())
        assertEquals(info, Json.decodeFromString(WsMsg.serializer(), file.readContents()))
        // clearing does not flip lockAcquired itself - the file watch does, within its poll period
        lock.tryClearLock()
        awaitLockState(lock, false)
        status.writeMsg(WsMsg.Idle)
        assertEquals(info.json(), file.readContents(), "released lock, write ignored")
    }

    /**
     * `initIfNeeded` goes through `writeMsg`, so it only seeds `Idle` while the lock is held - the
     * ingest worker calling it at startup without the lock writes nothing (pinned, not endorsed).
     */
    @Test
    fun `writes are atomic renames that leave no temp file and initIfNeeded only seeds Idle under the lock`() {
        val (dir, lock, status) = statusEnv("atomic")
        val file = File(dir, "status.json")
        status.initIfNeeded()
        assertFalse(file.exists(), "without the lock initIfNeeded is a no-op")
        assertTrue(lock.tryAcquireLock())
        status.initIfNeeded()
        assertEquals(WsMsg.Idle.json(), file.readContents())
        status.writeMsgIgnoreLock(WsMsg.Extracting(1f))
        status.initIfNeeded()
        assertEquals(WsMsg.Extracting(1f).json(), file.readContents(), "an existing status is not reset")
        assertEquals(listOf("lock", "status.json"), dir.listFiles(false).map { it.name }.sorted(), "no .tmp left behind")
    }

    // ---- P-CC-3 worker cadence ----------------------------------------------------------------

    @Test
    fun cadence() {
        val facts = mapOf(
            "ingest worker poll ms" to JsonPrimitive(ChartIngestWorker.POLL_DELAY_MS),
            "region worker poll ms (zips pending, lock held, or lock busy)" to JsonPrimitive(RegionExportWorker.POLL_DELAY_MS),
            "region worker idle ms (nothing to do)" to JsonPrimitive(RegionExportWorker.IDLE_DELAY_MS),
            "region worker after a render" to JsonPrimitive("loops again immediately"),
            "wake()" to JsonPrimitive("conflated signal; cuts a poll or idle wait short; called after ingest completes, chart delete and POST /v1/regions"),
            "ingest worker claim" to JsonPrimitive("one zip per lock cycle: acquire lock, clear ingest/, rename save/<zip> to ingest/<uuid>/<zip>"),
            "websocket status poll ms" to JsonPrimitive(500),
        )
        fixtures.table(
            "concurrency/cadence.json",
            FixtureHeader(
                pins = "Worker timing the replicas and enc_cron rely on: ingest poll, region export poll/idle, wake semantics, websocket status poll",
                decisions = listOf("RE-9", "IN-7", "CC-5"),
                generator = "ConcurrencyPrimitivesTest.cadence",
            ),
            facts.keys.map { SemanticsInput(it) },
        ) { facts[it.question]!! }
        assertEquals(5_000L, ChartIngestWorker.POLL_DELAY_MS)
        assertEquals(5_000L, RegionExportWorker.POLL_DELAY_MS)
        assertEquals(15 * 60 * 1000L, RegionExportWorker.IDLE_DELAY_MS)
    }
}
