package io.madrona.njord.db

import com.fasterxml.jackson.databind.ObjectMapper
import io.madrona.njord.Singletons
import io.madrona.njord.util.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

abstract class Dao(
    protected val objectMapper: ObjectMapper = Singletons.objectMapper,
    private val ds: DataSource = Singletons.ds,
    private val scope: CoroutineScope = Singletons.ioScope
) {
    protected val log = logger()

    fun <T> sqlOpAsync(msg: String = "error", block: (conn: Connection) -> T) : Deferred<T?> {
        return scope.async {
            try {
                ds.connection.use(block)
            } catch (e: SQLException) {
                log.error(msg, e)
                null
            }
        }
    }
}

sealed class Insertable<T>
class InsertError<T>(
    val msg: String
) : Insertable<T>()
class InsertSuccess<T>(
    val value: T
) : Insertable<T>()