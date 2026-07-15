package io.madrona.njord.ingest

import File
import io.madrona.njord.Singletons
import io.madrona.njord.util.DistributedLock
import io.madrona.njord.util.logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Continuously renders stale region archives, one region at a time.
 *
 * [run] loops indefinitely: while zips are queued for ingestion or the ingestion lock is held,
 * it waits briefly and rechecks. Otherwise it calls [RegionExporter.exportNext]: if a region was
 * rendered, it loops again immediately (more regions may still be stale); if nothing needed
 * rendering, it idles for [IDLE_DELAY_MS] before checking again. [wake] interrupts an idle/poll
 * wait early — called after ingestion completes so newly-ingested charts' regions render promptly
 * instead of waiting out the full idle period.
 */
class RegionExportWorker(
    private val saveDir: File = Singletons.chartUploadDir,
    private val distributedLock: DistributedLock = Singletons.distributedLock,
    private val exporter: RegionExporter = RegionExporter(),
) {
    private val log = logger()
    private val wakeChannel = Channel<Unit>(Channel.CONFLATED)

    fun wake() {
        wakeChannel.trySend(Unit)
    }

    suspend fun run() {
        while (true) {
            log.info("region export worker run")
            val pendingZips = saveDir.listFiles(false)
                .filter { it.name.endsWith(".zip", ignoreCase = true) }
            if (pendingZips.isNotEmpty() || distributedLock.lockAcquired) {
                idleFor(POLL_DELAY_MS)
                continue
            }
            val result = runCatching { exporter.exportNext() }
                .onFailure { log.error("region export error: ${it.message}") }
                .getOrElse { RegionExporter.ExportResult.LockBusy }
            when (result) {
                is RegionExporter.ExportResult.Rendered -> {
                    log.info("region export worker rendered")
                }
                is RegionExporter.ExportResult.LockBusy -> {
                    log.info("region export worker lock busy waiting for $POLL_DELAY_MS")
                    idleFor(POLL_DELAY_MS)
                }
                is RegionExporter.ExportResult.NothingToDo -> {
                    log.info("region export worker nothing to do $IDLE_DELAY_MS")
                    idleFor(IDLE_DELAY_MS)
                }
            }
        }
    }

    private suspend fun idleFor(ms: Long) {
        withTimeoutOrNull(ms) { wakeChannel.receive() }
    }

    companion object {
        private const val POLL_DELAY_MS = 5_000L
        private const val IDLE_DELAY_MS = 15 * 60 * 1000L
    }
}
