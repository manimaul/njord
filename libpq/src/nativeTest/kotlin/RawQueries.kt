@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import libpq.PGconn
import libpq.PQexec
import libpq.PQexecParams
import libpq.PQfinish
import libpq.PQfname
import libpq.PQfnumber
import libpq.PQgetisnull
import libpq.PQgetvalue
import libpq.PQnfields
import libpq.PQntuples
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RawQueries {

    lateinit var pgConn: CPointer<PGconn>

    @BeforeTest
    fun beforeEach() {
        val ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)
        runBlocking {
            ds.connection().use { conn ->
                pgConn = (conn as PgConnection).pgDb.conn
                conn.statement(testDbSql).execute()
            }
        }
    }

    @AfterTest
    fun afterEach() {
        PQfinish(pgConn)
    }


    @Test
    fun testSelect() {
        PQexec(pgConn, "insert into testing VALUES (1, 'bar'), (2, 'baz');").check(pgConn)
        val res = PQexec(
            pgConn,
            "select * from testing where name='baz';",
        ).check(pgConn)
        val maxRowIndex = PQntuples(res) - 1
        var currentRowIndex: Int = -1
        val numberOfFields: Int = PQnfields(res)
        val results = mutableListOf<String>()
        while (currentRowIndex < maxRowIndex) {
            ++currentRowIndex
            val id = PQfnumber(res, "id").takeIf {
                PQgetisnull(res, tup_num = currentRowIndex, field_num = it) != 1
            }?.let {
                PQgetvalue(res, currentRowIndex, it)?.toKString()?.toLongOrNull()
            }
            val name = PQfnumber(res, "name").takeIf {
                PQgetisnull(res, tup_num = currentRowIndex, field_num = it) != 1
            }?.let {
                PQgetvalue(res, currentRowIndex, it)?.toKString()
            }
            results.add("$id,$name")

            val keys = arrayOfNulls<String>(numberOfFields).let {
                for (i in 0 until numberOfFields) {
                    it[i] = PQfname(res, i)?.toKString()
                }
                it.filterNotNull()
            }

            res.clear()

            assertEquals(listOf("id", "name", "json_b", "array_t", "data_b", "truth"), keys)
            assertEquals(6, numberOfFields)
            assertEquals(listOf("2,baz"), results)
        }
    }

    @Test
    fun testSelectParams() {
        PQexec(pgConn, "insert into testing VALUES (1, 'bar'), (2, 'baz'), (3, 'zab');").check(pgConn)
        memScoped {
            val arg = "baz"
            val paramValues = arrayOfNulls<Data>(1)
            paramValues[0] = Data.Text(arg)
            val lengths = IntArray(paramValues.size)
            lengths[0] = arg.length
            val formats = IntArray(paramValues.size)
            formats[0] = TEXT_RESULT_FORMAT
            val types = UIntArray(paramValues.size)
            types[0] = 25u //textOid
            val res = PQexecParams(
                pgConn,
                "select * from testing where name!=$1",
                paramValues.size,
                types.refTo(0),
                values(this, paramValues),
                lengths.refTo(0),
                formats.refTo(0),
                TEXT_RESULT_FORMAT
            ).check(pgConn)

            val maxRowIndex = PQntuples(res) - 1
            var currentRowIndex: Int = -1
            val numberOfFields: Int = PQnfields(res)
            val results = mutableListOf<String>()
            while (currentRowIndex < maxRowIndex) {
                ++currentRowIndex
                val id = PQfnumber(res, "id").takeIf {
                    PQgetisnull(res, tup_num = currentRowIndex, field_num = it) != 1
                }?.let {
                    PQgetvalue(res, currentRowIndex, it)?.toKString()?.toLongOrNull()
                }
                val name = PQfnumber(res, "name").takeIf {
                    PQgetisnull(res, tup_num = currentRowIndex, field_num = it) != 1
                }?.let {
                    PQgetvalue(res, currentRowIndex, it)?.toKString()
                }
                results.add("$id,$name")
            }
            res.clear()
            assertEquals(6, numberOfFields)
            assertEquals(listOf("1,bar", "3,zab"), results)
        }
    }

    @Test
    fun testSelectParamsCursor() {
        PQexec(pgConn, "insert into testing VALUES (1, 'bar'), (2, 'baz'), (3, 'zab');").check(pgConn)

        memScoped {
            val arg = "baz"
            val paramValues = arrayOfNulls<Data>(1)
            paramValues[0] = Data.Text(arg)
            val lengths = IntArray(paramValues.size)
            lengths[0] = arg.length
            val formats = IntArray(paramValues.size)
            formats[0] = TEXT_RESULT_FORMAT
            val types = UIntArray(paramValues.size)
            types[0] = 25u //textOid

            println("beginning transaction")
            PQexec(pgConn, "BEGIN").check(pgConn)

            println("declaring cursor")
            PQexecParams(
                pgConn,
                "DECLARE my_cursor CURSOR FOR select * from testing where name!=$1",
                paramValues.size,
                types.refTo(0),
                values(this, paramValues),
                lengths.refTo(0),
                formats.refTo(0),
                TEXT_RESULT_FORMAT
            ).check(pgConn)

            println("fetching cursor results")
            val results = mutableListOf<String>()
            var numberOfFields = 0
            while (true) {
                val res = PQexec(pgConn, "FETCH FORWARD 1 FROM my_cursor").check(pgConn)

                if (PQntuples(res) == 0) {
                    break
                }

                numberOfFields = PQnfields(res)
                val id = PQfnumber(res, "id").takeIf {
                    PQgetisnull(res, tup_num = 0, field_num = it) != 1
                }?.let {
                    PQgetvalue(res, 0, it)?.toKString()?.toLongOrNull()
                }
                val name = PQfnumber(res, "name").takeIf {
                    PQgetisnull(res, tup_num = 0, field_num = it) != 1
                }?.let {
                    PQgetvalue(res, 0, it)?.toKString()
                }
                results.add("$id,$name")
                res.clear()
            }

            PQexec(pgConn, "CLOSE my_cursor").check(pgConn)
            PQexec(pgConn, "END").check(pgConn)

            assertEquals(6, numberOfFields)
            assertEquals(listOf("1,bar", "3,zab"), results)
        }
    }
}