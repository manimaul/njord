@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import libpq.PGresult
import libpq.PQexec
import libpq.PQexecParams

open class PgStatement(
    val sql: String,
    val pgDb: PgDb,
) : Statement {

    val parameters = pattern.findAll(sql).count()

    override fun executeQuery(): ResultSet {
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
            PgResultSet(cursor, result, pgDb.conn)
        } else {
            pgDb.conn.exec("DECLARE $cursor CURSOR FOR $sql")
            val result = PQexec(pgDb.conn, sql).check(pgDb.conn)
            return PgResultSet(cursor, result, pgDb.conn)
        }
    }

    private fun executeInternal(): CPointer<PGresult> {
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
            }.check(pgDb.conn)
        }

        return PQexec(
            pgDb.conn,
            query = sql,
        ).check(pgDb.conn)
    }

    override fun executeReturning(): ResultSet {
        return PgResultSet(result = executeInternal(), conn = pgDb.conn)
    }

    override fun execute(): Long {
        return executeInternal().rows
    }


    internal val values = arrayOfNulls<Data>(parameters)
    internal val lengths = IntArray(parameters)
    internal val formats = IntArray(parameters)
    internal val types = UIntArray(parameters)

    override fun setArray(index: Int, value: Array<Any>?): Statement {
        val sb = StringBuilder("{")
        value?.forEachIndexed { i, ea ->
            when (ea) {
                is Number -> {
                    sb.append(ea.toString())
                }

                is String -> {
                    sb.append('\"')
                    sb.append(ea)
                    sb.append('\"')
                }

                else -> {
                    sb.append('\"')
                    sb.append(ea.toString())
                    sb.append('\"')
                }
            }
            if (i < value.size - 1) {
                sb.append(',')
            }
        }
        sb.append('}')
        val arrStr = sb.toString()
        bind(index, arrStr, autoOid)
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

    override fun setAuto(index: Int, json: String?): Statement {
        bind(index, json, autoOid)
        return this
    }

    override fun setAuto(index: Int, value: ByteArray?): Statement {
        bind(index, value, autoOid)
        return this
    }

    override fun setBytes(index: Int, value: ByteArray?): Statement {
        bind(index, value, byteaOid)
        return this
    }

    private fun bind(index: Int, value: String?, oid: UInt) {
        if (index !in 1..parameters) {
            throw IllegalArgumentException("1 based index $index is out of bounds for $parameters parameters")
        }
        val zeroIndex = index - 1
        lengths[zeroIndex] = if (value != null) {
            values[zeroIndex] = Data.Text(value)
            value.length
        } else 0
        formats[zeroIndex] = TEXT_RESULT_FORMAT
        types[zeroIndex] = oid
    }

    fun bind(index: Int, value: ByteArray?, oid: UInt): Statement {
        if (index !in 1..parameters) {
            throw IllegalArgumentException("1 based index $index is out of bounds for $parameters parameters")
        }
        val zeroIndex = index - 1
        lengths[zeroIndex] = if (value != null && value.isNotEmpty()) {
            values[zeroIndex] = Data.Bytes(value)
            value.size
        } else 0
        formats[zeroIndex] = BINARY_RESULT_FORMAT
        types[zeroIndex] = oid
        return this
    }

    companion object {
        // select * from pg_type where typname like '%array';
        // select 'features'::regclass::oid;
        private const val boolOid = 16u
        private const val byteaOid = 17u
        private const val longOid = 20u
        private const val intOid = 23u
        private const val textOid = 25u

        /*
        INTEGER[],
        TEXT[],
        VARCHAR[],
        BOOLEAN[],
        NUMERIC[],
        DATE[],
        TIMESTAMP[],
        INTEGER[]
        TEXT[][]
         */
        private const val autoOid = 0u //2277u
        private const val floatOid = 700u
        private const val doubleOid = 701u
        private val pattern = "\\$[1-9]".toRegex()
    }
}
