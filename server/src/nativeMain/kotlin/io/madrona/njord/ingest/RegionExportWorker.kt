package io.madrona.njord.ingest

import File
import io.madrona.njord.Singletons
import io.madrona.njord.util.DistributedLock
import io.madrona.njord.util.logger
import kotlinx.coroutines.*

/**
 * Schedules region archive exports after chart ingestion completes.
 *
 * Trigger logic:
 *  - [schedule] is called each time the ingestion lock is released.
 *  - Any pending trigger is cancelled and reset to fire 15 seconds later.
 *  - After the delay, the worker checks that the upload queue is empty and the
 *    ingestion lock is clear before starting region generation.
 */
class RegionExportWorker(
    private val saveDir: File = Singletons.chartUploadDir,
    private val distributedLock: DistributedLock = Singletons.distributedLock,
    private val exporter: RegionExporter = RegionExporter(),
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val log = logger()
    private var pendingJob: Job? = null

    /**
     * Called after ingestion completes. Cancels any existing pending trigger and
     * schedules a new one to fire after [DELAY_MS] milliseconds.
     */
    fun schedule() {
        pendingJob?.cancel()
        pendingJob = launch {
            delay(DELAY_MS)
            runIfClear()
        }
    }

    private suspend fun runIfClear() {
        val pendingZips = saveDir.listFiles(false)
            .filter { it.name.endsWith(".zip", ignoreCase = true) }
        if (pendingZips.isNotEmpty()) {
            log.info("region export deferred — ${pendingZips.size} zip(s) still queued")
            return
        }
        if (distributedLock.lockAcquired) {
            log.info("region export deferred — ingestion lock is held")
            return
        }
        log.info("starting region export")
        runCatching { exporter.exportAll() }
            .onSuccess { log.info("region export complete") }
            .onFailure { log.error("region export error: ${it.message}") }
    }

    companion object {
        private const val DELAY_MS = 15_000L
    }
}
