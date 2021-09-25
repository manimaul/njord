package io.madrona.njord.db

import io.madrona.njord.Singletons
import io.madrona.njord.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

abstract class Dao(
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