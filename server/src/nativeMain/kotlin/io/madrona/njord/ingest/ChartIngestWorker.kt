package io.madrona.njord.ingest

import File
import io.madrona.njord.Singletons
import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.util.UUID
import io.madrona.njord.util.logger
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class ChartIngestWorker(
    private val saveDir: File = Singletons.chartUploadDir,
    private val workDir: File = Singletons.chartIngestWorkDir,
    private val statusFile: File = Singletons.ingestStatusFile,
) {
    private val log = logger()

    suspend fun run() {
        workDir.mkdirs()
        if (!statusFile.isFile()) {
            statusFile.writeMsg(WsMsg.Idle)
        }
        while (true) {
            delay(5_000)
            tryClaimAndIngest()
        }
    }

    private suspend fun tryClaimAndIngest() {
        // Only one ingestion at a time: skip if ingest/ has any subdirectory
        if (workDir.listDirs().isNotEmpty()) return

        val zips = saveDir.listFiles(false).filter { it.name.endsWith(".zip", ignoreCase = true) }
        if (zips.isEmpty()) return

        val uuid = UUID.randomUUID().toString()
        val claimDir = File(workDir, uuid)
        claimDir.mkdirs()

        // Atomic per-file claim: rename zip from save/ → ingest/{uuid}/
        val claimed = zips.mapNotNull { zip ->
            val dest = File(claimDir, zip.name)
            if (zip.renameTo(dest.getAbsolutePath().toString()) != null) dest else null
        }

        if (claimed.isEmpty()) {
            claimDir.deleteRecursively()
            return
        }

        log.info("claimed ${claimed.size} zip(s) for ingestion as $uuid")
        try {
            ChartIngest(statusFile = statusFile, chartDir = claimDir).ingest(
                EncUpload(zipFiles = claimed.map { it.name })
            )
        } catch (e: Throwable) {
            log.error("ingestion failed: ${e.message}")
            statusFile.writeMsg(WsMsg.Error(message = e.message ?: "ingestion error", isFatal = true))
            claimDir.deleteRecursively()
            statusFile.writeMsg(WsMsg.Idle)
        }
    }
}

fun File.writeMsg(msg: WsMsg) {
    val json = Json.encodeToString(WsMsg.serializer(), msg)
    val parentPath = path.parent?.toString() ?: "."
    val tmp = File("$parentPath/${name}.${kotlin.random.Random.nextLong()}.tmp")
    tmp.write(json)
    tmp.renameTo(getAbsolutePath().toString())
}
