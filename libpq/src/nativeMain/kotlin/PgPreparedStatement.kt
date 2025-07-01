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

    override fun executeQuery(): ResultSet {
//        val name = nextName()
//        if (!preparedStatementExists(identifier)) {
//            println("preparing statement for $sql")
//            PQprepare(
//                pgDb.conn,
//                stmtName = identifier.toString(),
//                query = sql,
//                nParams = parameters,
//                paramTypes = types.refTo(0)
//            ).check(pgDb.conn).clear()
//        } else {
//            println("statement for $sql prepared")
//        }
        return EmptyResultSet()
//        pgDb.conn.exec("BEGIN")
//        val result = memScoped {
//            PQexecPrepared(
//                pgDb.conn,
//                stmtName = identifier.toString(),
//                nParams = parameters,
//                paramValues = values(this, values),
//                paramLengths = lengths.refTo(0),
//                paramFormats = formats.refTo(0),
//                resultFormat = TEXT_RESULT_FORMAT
//            )
//        }.check(pgDb.conn)
//        return PgResultSet(name, result, pgDb.conn)
    }

    private fun preparedStatementExists(identifier: Int): Boolean {
        return PgStatement("SELECT name FROM pg_prepared_statements WHERE name = $1", pgDb).use {
            it.setInt(0, identifier)
            executeQuery().use { query ->
                query.next()
            }
        }
    }

    override fun execute(): Long {
        TODO("Not yet implemented")
    }


    override fun executeUpdate(): Int {
        TODO("Not yet implemented")
    }

    override fun <T> executeUpdateGeneratedKeys(handler: (Int, ResultSet) -> T): T {
        TODO("Not yet implemented")
    }
}
