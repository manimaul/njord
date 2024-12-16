package io.madrona.njord.db

import com.fasterxml.jackson.databind.ObjectMapper
import io.madrona.njord.Singletons
import io.madrona.njord.util.logger
import kotlinx.coroutines.*
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext

abstract class Dao(
    protected val objectMapper: ObjectMapper = Singletons.objectMapper,
    private val ds: DataSource = Singletons.ds,
) {
    protected val log = logger()

    suspend fun <T> sqlOpAsync(msg: String = "error", block: suspend (conn: Connection) -> T): T? = sqlOpAsyncInternal(msg, 1, block)

    private suspend fun <T> sqlOpAsyncInternal(msg: String, tryCount: Int, block: suspend (conn: Connection) -> T): T? {
        return try {
            ds.connection.use {
                block(it)
            }
        } catch (e: SQLException) {
            log.error("$msg - try count = $tryCount", e)
            if (tryCount < MAX_TRY_COUNT) {
                delay(1000)
                sqlOpAsyncInternal(msg, tryCount + 1, block)
            } else {
                null
            }
        }
    }

    companion object {
        const val MAX_TRY_COUNT = 250
    }
}

sealed class Insertable<T>
class InsertError<T>(
    val msg: String
) : Insertable<T>()

class InsertSuccess<T>(
    val value: T
) : Insertable<T>()
