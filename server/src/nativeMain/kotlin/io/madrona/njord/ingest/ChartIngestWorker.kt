package io.madrona.njord.ingest

import File
import ZipFile
import io.madrona.njord.Singletons
import io.madrona.njord.model.EncUpload
import io.madrona.njord.util.DistributedLock
import io.madrona.njord.util.logger
import kotlinx.coroutines.delay

class ChartIngestWorker(
    private val saveDir: File = Singletons.chartUploadDir,
    private val workDir: File = Singletons.chartIngestWorkDir,
    private val ingestStatus: IngestStatus = Singletons.ingestStatus,
    private val distributedLock: DistributedLock = Singletons.distributedLock,
) {
    private val log = logger()

    suspend fun run() {
        workDir.mkdirs()
        ingestStatus.initIfNeeded()
        while (true) {
            delay(5_000)
            tryClaimAndIngest()
        }
    }

    private suspend fun tryClaimAndIngest() {
        val zips = saveDir.listFiles(false).filter { it.name.endsWith(".zip", ignoreCase = true) }
        if (zips.isEmpty()) return

        if (!distributedLock.tryAcquireLock()) return
        println("lock acquired to perform chart ingestion")

        println("clearing work dir")
        workDir.deleteRecursively()

        val claimDir = File(workDir, distributedLock.uuid)
        claimDir.mkdirs()

        // Atomic per-file claim: rename zip from save/ → ingest/{uuid}/
        val claimed = zips.mapNotNull { zip ->
            val dest = File(claimDir, zip.name)
            if (zip.renameTo(dest.getAbsolutePath().toString()) != null) dest else null
        }

        if (claimed.isEmpty()) {
            println("claimed files empty - clearing lock and cleaning up")
            claimDir.deleteRecursively()
            distributedLock.tryClearLock()
            return
        }

        log.info("claimed ${claimed.size} zip(s) for ingestion as ${distributedLock.uuid}")
        val encUpload = EncUpload(zipFiles = claimed.map { it.name })
        val hasShapefiles = claimed.any { zip ->
            ZipFile(zip).entries().any { it.name().endsWith(".shp", ignoreCase = true) }
        }
        if (hasShapefiles) {
            NaturalEarthIngest(ingestStatus = ingestStatus, chartDir = claimDir).ingest(encUpload)
        } else {
            ChartIngest(ingestStatus = ingestStatus, chartDir = claimDir).ingest(encUpload)
        }
    }
}
