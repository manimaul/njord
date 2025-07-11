@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import libpq.PQexecPrepared
import libpq.PQprepare

class PgPreparedStatement(
    sql: String,
    pgDb: PgDb,
    val identifier: Int
) : PgStatement(sql, pgDb) {

    private fun Int.escapeNegative(): String = if (this < 0) "_${toString().substring(1)}" else toString()

    override fun executeQuery(): ResultSet {
        val cursorName = "cursor${identifier.escapeNegative()}"
        if (!preparedStatementExists()) {
            pgDbLogD("preparing statement for '$sql'")
            prepare(cursorName)
        } else {
            pgDbLogD("statement prepared for '$sql'")
        }
        pgDb.conn.exec("BEGIN")
        val result = memScoped {
            PQexecPrepared(
                pgDb.conn,
                stmtName = identifier.toString(),
                nParams = parameters,
                paramValues = values.takeIf { it.isNotEmpty() }?.let { values(this, it) },
                paramLengths = lengths.takeIf { it.isNotEmpty() }?.refTo(0),
                paramFormats = formats.takeIf { it.isNotEmpty() }?.refTo(0),
                resultFormat = TEXT_RESULT_FORMAT
            )
        }.check(pgDb.conn)
        return PgResultSet(cursorName, result, pgDb.conn)
    }

    fun prepare(name: String) {
        PQprepare(
            pgDb.conn,
            stmtName = identifier.toString(),
            query = "DECLARE $name CURSOR FOR $sql",
            nParams = parameters,
            paramTypes = types.takeIf { it.isNotEmpty() }?.refTo(0)
        ).check(pgDb.conn).clear()
    }

    fun preparedStatementExists(): Boolean {
        return PgStatement("SELECT name FROM pg_prepared_statements WHERE name = $1", pgDb).let {
            it.setString(1, "$identifier")
            it.executeQuery().use { query ->
                query.next()
            }
        }
    }

    override fun execute(): Long {
//        return onExec(this)
//        if (identifier == null) {
//            if (parameters == 0) {
//                return memScoped {
//                    PQexec(
//                        conn =connection.pgDb.conn,
//                        query = sql
//                    ).check(connection.pgDb.conn)
//                }.rows
//            }
//        }
//        return if (identifier == null) {
//            memScoped {
//                PQexecParams(
//                    connection.pgDb.conn,
//                    nParams = parameters,
//                    paramValues = values(this),
//                    paramFormats = formats.refTo(0),
//                    paramLengths = lengths.refTo(0),
//                    resultFormat = TEXT_RESULT_FORMAT,
//                   paramTypes = prp
//                )
//            }.check(connection.pgDb.conn).rows
//        } else {
//            //todo: check for cached statement
//            memScoped {
//                PQexecPrepared(
//                    connection.pgDb.conn,
//                    stmtName = identifier.toString(),
//                    nParams = parameters,
//                    paramValues = values(this),
//                    paramFormats = formats.refTo(0),
//                    paramLengths = lengths.refTo(0),
//                    resultFormat = TEXT_RESULT_FORMAT
//                )
//            }.check(connection.pgDb.conn).rows
//        }
        TODO()
    }

    override fun <T> executeUpdate(handler: (Int, ResultSet) -> T): T {
        TODO("Not yet implemented")
    }
}
