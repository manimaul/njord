package io.madrona.njord.db

import File
import io.madrona.njord.util.logger
import kotlin.random.Random

/**
 * Best-effort filesystem tile cache.
 *
 * Layout: $basePath/{z}/{x}/{y}.mvt
 *
 * All I/O errors are caught and logged — the cache is a performance
 * optimization, never a correctness requirement. Callers must handle null
 * from [get] by computing the tile normally.
 *
 * [clear] deletes the entire cache tree and is called after chart ingestion
 * so that stale tiles are not served.
 */
class TileCache(private val basePath: File) {

    private val log = logger()

    init {
        basePath.mkdirs()
    }

    fun get(z: Int, x: Int, y: Int): ByteArray? {
        return try {
            val f = File("$basePath/$z/$x/$y.mvt")
            if (f.isFile()) f.readData().takeIf { it.isNotEmpty() } else null
        } catch (e: Throwable) {
            log.warn("tile cache read error $z/$x/$y: ${e.message}")
            null
        }
    }

    /**
     *  Stores the tile in the cache.
     *
     *  Notes: We rename the file to eliminate a torn-read risk when the directory is a shared nfs (PVC) mount on K8S.
     */
    fun put(z: Int, x: Int, y: Int, data: ByteArray) {
        if (data.isEmpty()) return
        try {
            val dir = File("$basePath/$z/$x")
            if (!dir.isDirectory()) dir.mkdirs()
            val tmp = File("$basePath/$z/$x/$y.mvt.${Random.nextLong(Long.MAX_VALUE)}.tmp")
            tmp.writeBytes(data)
            if (tmp.renameTo("$basePath/$z/$x/$y.mvt") == null) {
                log.warn("tile cache rename failed $z/$x/$y")
                tmp.deleteRecursively()
            }
        } catch (e: Throwable) {
            log.warn("tile cache write error $z/$x/$y: ${e.message}")
        }
    }

    fun clear() {
        try {
            if (basePath.isDirectory()) {
                basePath.deleteRecursively()
                basePath.mkdirs()
                log.info("tile cache cleared")
            }
        } catch (e: Throwable) {
            log.warn("tile cache clear error: ${e.message}")
        }
    }
}
