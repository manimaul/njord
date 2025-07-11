@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import libpq.PQexec
import libpq.PQexecParams

open class PgStatement(
    val sql: String,
    val pgDb: PgDb,
) : Statement {

    val parameters = pattern.findAll(sql).count()

    override fun executeQuery(): ResultSet {
        pgDbLogD("starting transaction")
        pgDb.conn.exec("BEGIN")
        val cursor = generateRandomString(8)
        return if (parameters > 0) {
            val result = memScoped {
                PQexecParams(
                    conn = pgDb.conn,
                    command = "DECLARE $cursor CURSOR FOR $sql",
                    nParams = parameters,
                    paramValues = values(this, values),
                    paramLengths = lengths.refTo(0),
                    paramFormats = formats.refTo(0),
                    paramTypes = types.refTo(0),
                    resultFormat = TEXT_RESULT_FORMAT
                )
            }.check(pgDb.conn)
            pgDbLogD("opening cursor '$cursor'")
            PgResultSet(cursor, result, pgDb.conn)
        } else {
            pgDb.conn.exec("DECLARE $cursor CURSOR FOR $sql")
            val result = memScoped {
                PQexec(pgDb.conn, sql).check(pgDb.conn)
            }.check(pgDb.conn)
            pgDbLogD("opening cursor '$cursor'")
            return PgResultSet(cursor, result, pgDb.conn)
        }
    }


    override fun execute(): Long {
        if (parameters > 0) {
            return memScoped {
                PQexecParams(
                    pgDb.conn,
                    command = sql,
                    nParams = parameters,
                    paramValues = values(this, values),
                    paramLengths = lengths.refTo(0),
                    paramFormats = formats.refTo(0),
                    paramTypes = types.refTo(0),
                    resultFormat = TEXT_RESULT_FORMAT
                )
            }.check(pgDb.conn).rows
        }
        return memScoped {
            PQexec(
                pgDb.conn,
                query = sql,
            )
        }.check(pgDb.conn).rows
    }


    override fun <T> executeUpdate(handler: (Int, ResultSet) -> T): T {
        TODO("Not yet implemented")
    }


    internal val values = arrayOfNulls<Data>(parameters)
    internal val lengths = IntArray(parameters)
    internal val formats = IntArray(parameters)
    internal val types = UIntArray(parameters)

    private fun bind(index: Int, value: String?, oid: UInt) {
        val zeroIndex = index - 1
        lengths[zeroIndex] = if (value != null) {
            values[zeroIndex] = Data.Text(value)
            value.length
        } else 0
        formats[zeroIndex] = TEXT_RESULT_FORMAT
        types[zeroIndex] = oid
    }

    override fun setArray(index: Int, value: Array<String>?): Statement {
        TODO("Not yet implemented")
        return this
    }

    override fun setLong(index: Int, value: Long?): Statement {
        bind(index, value?.toString(), longOid)
        return this
    }

    override fun setInt(index: Int, value: Int?): Statement {
        bind(index, value?.toString(), intOid)
        return this
    }

    override fun setBool(index: Int, value: Boolean?): Statement {
        bind(index, value?.toString(), boolOid)
        return this
    }

    override fun setFloat(index: Int, value: Float?): Statement {
        bind(index, value?.toString(), floatOid)
        return this
    }

    override fun setDouble(index: Int, value: Double?): Statement {
        bind(index, value?.toString(), doubleOid)
        return this
    }

    override fun setString(index: Int, value: String?): Statement {
        bind(index, value, textOid)
        return this
    }

    override fun setJsonb(index: Int, json: String?): Statement {
        bind(index, json, jsonbOid)
        return this
    }

    override fun setBytes(index: Int, value: ByteArray?): Statement {
        lengths[index] = if (value != null && value.isNotEmpty()) {
            values[index] = Data.Bytes(value)
            value.size
        } else 0
        formats[index] = BINARY_RESULT_FORMAT
        types[index] = byteaOid
        return this
    }

    companion object {
        // select * from pg_type;
        // select 'features'::regclass::oid;
        private const val boolOid = 16u
        private const val byteaOid = 17u
        private const val longOid = 20u
        private const val intOid = 23u
        private const val textOid = 25u
        private const val textArrayOid = 19654u
        private const val floatOid = 700u
        private const val doubleOid = 701u
        private const val jsonbOid = 3082u
        private val pattern = "\\$[1-9]".toRegex()
    }
}
