@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libpq.ConnStatusType
import libpq.PQreset
import libpq.PQstatus

/**
postgresql://
postgresql://localhost
postgresql://localhost:5433
postgresql://localhost/mydb
postgresql://user@localhost
postgresql://user:secret@localhost
postgresql://other@localhost/otherdb?connect_timeout=10&application_name=myapp
postgresql://host1:123,host2:456/somedb?target_session_attrs=any&application_name=myapp
 */
class PgDataSource(
    private val connectionInfo: String,
    connectionCount: Int = 10,
) : DataSource, CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO), AutoCloseable {

    private val ready: ArrayDeque<PgDb> =
        ArrayDeque((0 until connectionCount).map { PgDb.connect(connectionInfo) })
    private val waiting: ArrayDeque<Box> = ArrayDeque()
    private val mutex = Mutex()

    val readyCount: Int
        get() = ready.count()

    val waitingCount: Int
        get() = waiting.count()

    private fun PgDb.validated(): PgDb {
        if (PQstatus(conn) == ConnStatusType.CONNECTION_OK) return this
        PQreset(conn)
        if (PQstatus(conn) == ConnStatusType.CONNECTION_OK) return this
        close()
        return PgDb.connect(connectionInfo)
    }

    private suspend fun acquire(): PgDb {
        val box = mutex.withLock {
            if (ready.isNotEmpty()) {
                Box(ready.removeFirst())
            } else {
                Box().also { waiting.addLast(it) }
            }
        }
        var pgDb: PgDb? = box.pgDb
        while (pgDb == null) {
            delay(5)
            pgDb = box.pgDb
        }
        return pgDb.validated()
    }

    private fun release(pgDb: PgDb) {
        launch {
            mutex.withLock {
                if (waiting.isNotEmpty()) {
                    waiting.removeFirst().pgDb = pgDb
                } else {
                    ready.addLast(pgDb)
                }
            }
        }
    }

    override suspend fun connection(): Connection {
        return async {
            val pgDb = acquire()
            PgConnection(pgDb) {
                release(pgDb)
            }
        }.await()
    }

    override fun close() {
        runBlocking {
            mutex.withLock {
                while (waiting.isNotEmpty()) {
                    waiting.removeFirst().pgDb?.close()
                }
                while (ready.isNotEmpty()) {
                    ready.removeFirst().close()
                }
            }
        }
    }
}

private data class Box(
    var pgDb: PgDb? = null
)

class PgConnection(
    val pgDb: PgDb,
    val onClose: () -> Unit
) : Connection {


    override fun statement(sql: String): Statement {
        return PgStatement(sql, pgDb)
    }

    override fun prepareStatement(sql: String): Statement {
        return PgPreparedStatement(sql, pgDb, sql.hashCode())
    }

    override fun prepareStatement(sql: String, identifier: Int): Statement {
        return PgPreparedStatement(sql, pgDb, identifier)
    }

    override fun close() {
        onClose()
    }
}

internal sealed interface Data {
    value class Bytes(val bytes: ByteArray) : Data
    value class Text(val text: String) : Data
}

@ExperimentalForeignApi
internal fun values(scope: AutofreeScope, data: Array<Data?>): CValuesRef<CPointerVar<ByteVar>> =
    createValues(data.size) {
        value = when (val value = data[it]) {
            null -> null
            is Data.Bytes -> value.bytes.refTo(0).getPointer(scope)
            is Data.Text -> value.text.cstr.getPointer(scope)
        }
    }

