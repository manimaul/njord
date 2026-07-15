import io.madrona.njord.util.DistributedLock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `RegionExporter.exportNext()`/`exportForced()` both rely on [DistributedLock.tryAcquireLock]
 * for cross-process mutual exclusion of region rendering (sharing the same lock chart ingestion
 * uses) — this exercises that primitive directly, simulating two processes via two instances
 * pointed at the same lock file.
 */
class DistributedLockTest {

    @Test
    fun `only one instance holds the lock at a time`() {
        val lockFile = File("./build/tmp/test_data/distributed_lock_test/lock")
        lockFile.parentFile()?.mkdirs()
        lockFile.deleteRecursively()

        val first = DistributedLock(lockFile = lockFile)
        val second = DistributedLock(lockFile = lockFile)

        assertTrue(first.tryAcquireLock())
        assertFalse(second.tryAcquireLock())

        assertTrue(first.tryClearLock())
        assertTrue(second.tryAcquireLock())
        assertFalse(first.tryAcquireLock())
    }
}
