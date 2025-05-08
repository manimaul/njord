import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import libpq.*

const val TEXT_RESULT_FORMAT = 0
const val BINARY_RESULT_FORMAT = 1

@OptIn(ExperimentalForeignApi::class)
class PgDb(
    private val conn: CPointer<PGconn>,
) {

    fun execute(
        sql: String,
    ): Long {

        val result = memScoped {
            PQexec(
                conn,
                query = sql,
            )
        }.check(conn)
        return result.rows
    }

    fun query(sql: String): ResultSet {
        conn.exec("BEGIN")
        val name = nextName()
        conn.exec("DECLARE $name CURSOR FOR $sql")
        val result = memScoped {
            PQexecParams(
                conn,
                command = sql,
                nParams = 0,
                paramValues = null,
                paramFormats = null,
                paramLengths = null,
                resultFormat = TEXT_RESULT_FORMAT,
                paramTypes = null
            )
        }.check(conn)
        return PgResultSet(name, result, conn)
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

    private val CPointer<PGresult>.rows: Long
        get() {
            val rows = PQcmdTuples(this)!!.toKString()
            clear()
            return rows.toLongOrNull() ?: 0
        }
}

@ExperimentalForeignApi
fun CPointer<PGresult>?.check(conn: CPointer<PGconn>): CPointer<PGresult> {
    val status = PQresultStatus(this)
    check(status == PGRES_TUPLES_OK || status == PGRES_COMMAND_OK || status == PGRES_COPY_IN) {
        clear()
        conn.error()
    }
    return this!!
}

@ExperimentalForeignApi
private fun CPointer<PGconn>?.error(): String {
    val errorMessage = PQerrorMessage(this)!!.toKString()
    PQfinish(this)
    return errorMessage
}

@ExperimentalForeignApi
internal fun CPointer<PGresult>?.clear() {
    PQclear(this)
}

@ExperimentalForeignApi
internal fun CPointer<PGconn>.exec(sql: String) {
    val result = PQexec(this, sql)
    result.check(this)
    result.clear()
}


@ExperimentalForeignApi
private fun CPointer<PGconn>.escaped(value: String): String {
    val cString = PQescapeIdentifier(this, value, value.length.convert())
    val escaped = cString!!.toKString()
    PQfreemem(cString)
    return escaped
}

