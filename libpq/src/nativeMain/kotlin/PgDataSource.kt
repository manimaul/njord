@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bounded pool of [PgDb] connections. Connections are created lazily (up to [poolSize]) and
 * reused via [idle] rather than opening a new TCP socket per query — with zero pooling, query
 * volume translates 1:1 into concurrently-open OS socket descriptors, which is how a big region
 * export (thousands of sequential queries) previously blew past FD_SETSIZE.
 */
class PgDataSource(
    private val connectionInfo: String,
    private val poolSize: Int = 10,
) : DataSource {
    private val idle = Channel<PgDb>(capacity = poolSize)
    private val initMutex = Mutex()
    private var created = 0

    override suspend fun connection(): Connection? {
        val pgDb = acquire() ?: return null

        val healthy = if (pgDb.isHealthy()) {
            pgDb
        } else {
            pgDb.close()
            PgDb.connect(connectionInfo) ?: return null
        }

        return PgConnection(pgDb = healthy, onClose = {
            if (!idle.trySend(healthy).isSuccess) healthy.close()
        })
    }

    /**
     * Returns an idle connection, or grows the pool (up to [poolSize]) and returns a fresh one.
     * If the pool is already at capacity, suspends until one is returned. A failed *connect*
     * attempt (DB unreachable) returns null immediately rather than falling through to an
     * indefinite [idle] wait.
     */
    private suspend fun acquire(): PgDb? {
        idle.tryReceive().getOrNull()?.let { return it }

        var atCapacity = false
        val created0 = initMutex.withLock {
            if (created < poolSize) {
                PgDb.connect(connectionInfo)?.also { created++ }
            } else {
                atCapacity = true
                null
            }
        }
        return created0 ?: if (atCapacity) idle.receive() else null
    }
}

class PgConnection(
    val pgDb: PgDb,
    val onClose: () -> Unit
) : Connection {

    override fun statement(sql: String): Statement {
        return PgStatement(sql, pgDb)
    }

    override fun prepareStatement(sql: String): Statement {
        return PgStatement(sql, pgDb)
    }

    override fun prepareStatement(sql: String, identifier: Int): Statement {
        return PgStatement(sql, pgDb)
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
