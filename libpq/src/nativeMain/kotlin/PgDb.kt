@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import libpq.*

const val TEXT_RESULT_FORMAT = 0
const val BINARY_RESULT_FORMAT = 1

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
                conn.error(true)
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
                conn.error(true)
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
//    val statusStr = when (status) {
//        PGRES_EMPTY_QUERY -> "PGRES_EMPTY_QUERY"
//        PGRES_COMMAND_OK -> "PGRES_COMMAND_OK"
//        PGRES_TUPLES_OK -> "PGRES_TUPLES_OK"
//        PGRES_COPY_OUT -> "PGRES_COPY_OUT"
//        PGRES_COPY_IN -> "PGRES_COPY_IN"
//        PGRES_BAD_RESPONSE -> "PGRES_BAD_RESPONSE"
//        PGRES_NONFATAL_ERROR -> "PGRES_NONFATAL_ERROR"
//        PGRES_FATAL_ERROR -> "PGRES_FATAL_ERROR"
//        PGRES_COPY_BOTH -> "PGRES_COPY_BOTH"
//        else -> "$status"
//    }
//    println("check status=$$statusStr")
    check(status == PGRES_TUPLES_OK || status == PGRES_COMMAND_OK || status == PGRES_COPY_IN) {
        clear()
        conn.error(false)
    }
    return checkNotNull(this)
}

private fun CPointer<PGconn>?.error(finish: Boolean): String {
    val errorMessage = PQerrorMessage(this)?.toKString()
    if (errorMessage?.isNotEmpty() == true) {
        println("error = $errorMessage")
    }
    if (finish) {
        this?.let {
            PQfinish(it)
        }
    }

    return errorMessage ?: ""
}

internal fun CPointer<PGresult>?.clear() {
    PQclear(this)
}

internal fun CPointer<PGconn>.exec(sql: String) {
    val result = PQexec(this, sql)
    result.check(this)
    result.clear()
}
