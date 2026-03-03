package io.madrona.njord.db

import Connection
import DataSource
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
        val conn = ds.connection()
        if (conn == null) {
            if (tryCount >= maxTryCount) return null
            delay(100L * tryCount)
            return sqlOpAsyncInternal(msg, tryCount + 1, block, maxTryCount)
        }

        return conn.use {
            val result = runCatching {
                block(it)
            }

           if (result.isFailure && result.exceptionOrNull() !is CancellationException && tryCount < maxTryCount) {
               delay(100L * tryCount)
               sqlOpAsyncInternal(msg, tryCount + 1, block, maxTryCount)
           } else {
               result.getOrNull()
           }
        }
    }

    companion object {
        const val MAX_TRY_COUNT = 7
    }
}

