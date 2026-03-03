@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*

class PgDataSource(
    private val connectionInfo: String,
) : DataSource {
    override suspend fun connection(): Connection? {
        return PgDb.connect(connectionInfo)?.let { pgDb ->
            PgConnection(pgDb = pgDb, onClose = { pgDb.close() })
        }
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
