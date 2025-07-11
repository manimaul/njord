@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import libpq.*

const val TEXT_RESULT_FORMAT = 0
const val BINARY_RESULT_FORMAT = 1

var pgDebug = false

fun pgDbLogD(msg: String) {
    if (pgDebug) {
        println(msg)
    }
}

private val charPool: List<Char> = (('a'..'z') + ('A'..'Z'))

fun generateRandomString(length: Int): String {
    return List(length) { charPool.random() }.joinToString("")
}

class PgDb(
    val conn: CPointer<PGconn>,
) : AutoCloseable {

    override fun close() {
        PQfinish(conn)
    }

    companion object {
        fun connect(info: String) : PgDb {
            val conn = PQconnectdb(info)
            val status = PQstatus(conn)
            require(status == ConnStatusType.CONNECTION_OK) {
                conn.error()
            }
            return PgDb(conn!!)
        }

        fun login(host: String, port: Int, database: String, user: String, password: String): PgDb {
            val conn = PQsetdbLogin(
                pghost = host,
                pgport = port.toString(),
                pgtty = null,
                dbName = database,
                login = user,
                pwd = password,
                pgoptions = null
            )
            val status = PQstatus(conn)
            require(status == ConnStatusType.CONNECTION_OK) {
                conn.error()
            }
            return PgDb(conn!!)
        }
    }

}

val CPointer<PGresult>.rows: Long
    get() {
        val rows = PQcmdTuples(this)!!.toKString()
        clear()
        return rows.toLongOrNull() ?: 0
    }

fun CPointer<PGresult>?.check(conn: CPointer<PGconn>): CPointer<PGresult> {
    val status = PQresultStatus(this)
    if (pgDebug) {
        val statusStr = when(status) {
            PGRES_EMPTY_QUERY -> "PGRES_EMPTY_QUERY"
            PGRES_COMMAND_OK -> "PGRES_COMMAND_OK"
            PGRES_TUPLES_OK -> "PGRES_TUPLES_OK"
            PGRES_COPY_OUT -> "PGRES_COPY_OUT"
            PGRES_COPY_IN -> "PGRES_COPY_IN"
            PGRES_BAD_RESPONSE -> "PGRES_BAD_RESPONSE"
            PGRES_NONFATAL_ERROR -> "PGRES_NONFATAL_ERROR"
            PGRES_FATAL_ERROR -> "PGRES_FATAL_ERROR"
            PGRES_COPY_BOTH -> "PGRES_COPY_BOTH"
            else -> "$status"
        }
        pgDbLogD("status ($status) = $statusStr")
    }
    check(status == PGRES_TUPLES_OK || status == PGRES_COMMAND_OK || status == PGRES_COPY_IN) {
        clear()
        conn.error()
    }
    return checkNotNull(this)
}

private fun CPointer<PGconn>?.error(): String {
    val errorMessage = PQerrorMessage(this)!!.toKString()
    if (errorMessage.isNotEmpty()) {
        pgDbLogD("error = $errorMessage")
    }
    PQfinish(this)
    return errorMessage
}

internal fun CPointer<PGresult>?.clear() {
    PQclear(this)
}

internal fun CPointer<PGconn>.exec(sql: String) {
    val result = PQexec(this, sql)
    result.check(this)
    result.clear()
}

private fun CPointer<PGconn>.escaped(value: String): String {
    val cString = PQescapeIdentifier(this, value, value.length.convert())
    val escaped = cString!!.toKString()
    PQfreemem(cString)
    return escaped
}

