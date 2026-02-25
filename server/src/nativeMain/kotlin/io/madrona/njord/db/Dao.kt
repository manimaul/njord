package io.madrona.njord.db

import Connection
import DataSource
import SQLException
import io.madrona.njord.Singletons
import io.madrona.njord.util.logger
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

abstract class Dao(
    private val ds: DataSource = Singletons.ds,
) {
    protected val log = logger()

    suspend fun <T> sqlOpAsync(msg: String = "error", block: suspend (conn: Connection) -> T): T? = sqlOpAsyncInternal(msg, 1, block)

    suspend fun <T> sqlOpAsync(msg: String = "error", tryCount: Int, block: suspend (conn: Connection) -> T): T? = sqlOpAsyncInternal(msg, 1, block, tryCount)

    private suspend fun <T> sqlOpAsyncInternal(msg: String, tryCount: Int, block: suspend (conn: Connection) -> T, maxTryCount: Int = MAX_TRY_COUNT): T? {
        return try {
            ds.connection().use {
                block(it)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("$msg - try count = $tryCount", e)
            if (tryCount < maxTryCount) {
                delay(1000)
                sqlOpAsyncInternal(msg, tryCount + 1, block, maxTryCount)
            } else {
                null
            }
        }
    }

    companion object {
        const val MAX_TRY_COUNT = 15
    }
}

