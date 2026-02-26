@file:OptIn(ExperimentalForeignApi::class)

package io.madrona.njord.ingest

import File
import io.madrona.njord.Singletons
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.util.DistributedLock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.serialization.json.Json

class IngestStatus(
    val statusFile: File = Singletons.ingestStatusFile,
    val distributedLock: DistributedLock = Singletons.distributedLock,
) : CoroutineScope by CoroutineScope(Dispatchers.IO)  {

    fun initIfNeeded() {
        if (!statusFile.isFile()) writeMsg(WsMsg.Idle)
    }

    fun writeMsg(msg: WsMsg) {
        if (distributedLock.lockAcquired) {
            writeMsgIgnoreLock(msg)
        }
    }

    fun writeMsgIgnoreLock(msg: WsMsg) {
        val json = Json.encodeToString(WsMsg.serializer(), msg)
        val parentPath = statusFile.path.parent?.toString() ?: "."
        val tmp = File("$parentPath/${statusFile.name}.${kotlin.random.Random.nextLong()}.tmp")
        tmp.write(json)
        val f = tmp.renameTo(statusFile.getAbsolutePath().toString())
        println("$msg written ${f != null}")
    }
}
