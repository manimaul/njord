import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


private const val testDbSql = """
    drop table if exists testing;
    create table testing 
    (
        id      BIGSERIAL PRIMARY KEY, 
        name    VARCHAR   UNIQUE NOT NULL,
        json_b  JSONB     NULL,
        array_t VARCHAR[] NULL,
        data_b  BYTEA     NULL,
        truth   BOOLEAN   DEFAULT FALSE
    );
"""

class PgStatementTest {

    lateinit var ds: PgDataSource

    @BeforeTest
    fun beforeEach() {
        ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)
        runBlocking {
            ds.connection().use { conn ->
                conn.statement(testDbSql).execute()
            }
        }
    }

    @AfterTest
    fun afterEach() {
        ds.close()
    }


    @Test
    fun testStatement() {
        val chartNames = runBlocking {
            ds.connection().use { conn ->
                conn.statement(
                    "insert into testing VALUES (1, 'foo'), (2, 'bar'), (3, 'baz');"
                ).execute()
                conn.statement("SELECT name from testing LIMIT 3;").executeQuery().use { result ->
                    val names = mutableListOf<String>()
                    while (result.next()) {
                        val name = result.getString(1)
                        names.add(name)
                    }
                    names
                }
            }
        }

        assertEquals(
            listOf(
                "foo",
                "bar",
                "baz",
            ), chartNames
        )
    }

    @Test
    fun testStatementParams() {
        val names = runBlocking {
            ds.connection().use { conn ->
                conn.statement(
                    "insert into testing VALUES (1, 'foo'), (2, 'bar'), (3, 'baz');"
                ).execute()
                conn.statement("SELECT name from testing LIMIT $1;").setInt(1, 2).executeQuery().use { result ->
                    val names = mutableListOf<String>()
                    println("result rows ${result.totalRows}")
                    while (result.next()) {
                        val name = result.getString(1)
                        names.add(name)
                    }
                    names
                }
            }
        }

        assertEquals(
            listOf(
                "foo",
                "bar",
            ), names
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testExecute() {
        runBlocking {
            ds.connection().use { conn ->
                val count = conn.statement("insert into testing VALUES (1, 'bar'), (2, 'baz');").execute()
                val stmt = conn.statement("insert into testing VALUES ($1, $2), ($3, $4);")
                    .setLong(1, 3L)
                    .setString(2, "rab")
                    .setLong(3, 4L)
                    .setString(4, "zab")
                assertEquals(4, (stmt as PgStatement).parameters)
                val count2 = stmt.execute()
                assertEquals(2, count)
                assertEquals(2, count2)
            }

            ds.connection().use { conn ->
                conn.statement("select * from testing;").executeQuery().use { results ->
                    while (results.next()) {
                        println("${results.getLong("id")},${results.getString("name")}")
                    }
                }
            }
        }
    }

    @Test
    fun testExecute500Params() {
        runBlocking {
            ds.connection().use { conn ->
                var sql = "insert into testing VALUES "
                (1..499).forEach {
                    if (it % 2 == 1) {
                        sql += "(\$$it, \$${it + 1})${if (it == 499) ";" else ", "}"
                    }
                }
                println("creating statement for sql = '$sql'")
                val stmt = conn.statement(sql)
                assertEquals(500, (stmt as PgStatement).parameters)
                println("binding params")
                (1..499).forEach {
                    if (it % 2 == 1) {
                        stmt.setLong(it, 10000L + it)
                        stmt.setString(it + 1, "value ${it + 1}")
                    }
                }
                println("executing insert")
                stmt.execute()

                var count = 0
                println("creating select statement")
                val select = conn.statement("select * from testing;")
                println("executing select staement")
                select.executeQuery().use { resultSet ->
                    assertEquals(250, resultSet.totalRows)
                    while (resultSet.next()) {
                        assertEquals(250, resultSet.totalRows)
                        val id = resultSet.getLong(1)
                        val name = resultSet.getString(2)
                        println("result record id: $id value = $name")
                        count++
                    }
                }
                assertEquals(250, count)
            }

            ds.connection().use { conn ->
                var count = 0
                conn.prepareStatement("select * from testing;").executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val id = resultSet.getLong("id")
                        val name = resultSet.getString("name")
                        println("result record id: $id value = $name")
                        count++
                    }
                }
                assertEquals(250, count)
                count = 0
                conn.statement("select * from testing;").executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val id = resultSet.getLong(1)
                        val name = resultSet.getString(2)
                        println("result record id: $id value = $name")
                        count++
                    }
                }
                assertEquals(250, count)
            }
        }
    }

    @Test
    fun testUpdates() {
        var bazId: Long? = null
        var barId: Long? = null
        runBlocking {
            ds.connection().use { conn ->
                println("===== inserting bar, baz")
                conn.statement("insert into testing VALUES (1, 'bar'), (2, 'baz') returning *;").executeReturning()
                    .use { result ->
                        assertEquals(2, result.totalRows)

                        assertTrue(result.next())
                        barId = result.getLong("id")
                        println("${result.getLong("id")}, ${result.getString("name")}")
                        assertEquals("bar", result.getString("name"))

                        assertTrue(result.next())
                        println("${result.getLong("id")}, ${result.getString("name")}")
                        bazId = result.getLong("id")
                        assertEquals("baz", result.getString("name"))

                        assertFalse(result.next())
                    }

                println("===== updating bazId $bazId name to foo")
                conn.statement("update testing set name=$1 where id=$2 returning *;")
                    .setString(1, "foo")
                    .setLong(2, bazId)
                    .executeReturning().use { result ->
                        assertEquals(1, result.totalRows)
                        assertTrue(result.next())
                        println("${result.getLong("id")}, ${result.getString("name")}")
                        assertEquals("foo", result.getString("name"))
                    }

                println("===== updating bazId $bazId name to pil")
                conn.statement("update testing set name=$1 where id=$2 returning *;")
                    .setString(1, "pil")
                    .setLong(2, bazId)
                    .executeReturning().let { result ->
                        assertEquals(1, result.totalRows)
                        assertTrue(result.next())
                        println("${result.getLong("id")}, ${result.getString("name")}")
                        assertEquals("pil", result.getString("name"))
                    }

                println("===== querying * expecting it to contain $bazId,pil")
                conn.statement("select * from testing;").executeQuery().use { result ->
                    val list = mutableListOf<String>()
                    while (result.next()) {
                        list.add("${result.getLong("id")},${result.getString("name")}")
                    }
                    list.forEach {
                        println(it)
                    }
                    assertTrue(list.contains("$bazId,pil"))
                    assertEquals(2, list.size)
                }

                println("===== querying bazId $bazId expecting name to be pil")
                conn.statement("select * from testing where id=$1 or id=$2 order by id;")
                    .setLong(1, bazId)
                    .setLong(2, barId)
                    .executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(2, result.cursorRows)
                        assertEquals(1L, result.getLong("id"))
                        assertEquals("bar", result.getString("name"))

                        assertTrue(result.next())
                        assertEquals(2, result.cursorRows)
                        assertEquals(2L, result.getLong("id"))
                        assertEquals("pil", result.getString("name"))

                        assertFalse(result.next())
                    }
            }
        }
    }

    @Test
    fun testBinaryData() {
        runBlocking {
            ds.connection().use { conn ->
                conn.statement("insert into testing (id, name, data_b) VALUES ($1, $2, $3), ($4, $5, $6) returning *;")
                    .setLong(1, 0L)
                    .setString(2, "foo")
                    .setBytes(3, byteArrayOf(9, 1, 1))
                    .setLong(4, 1L)
                    .setString(5, "bar")
                    .setBytes(6, byteArrayOf(3, 5, 7))
                    .executeReturning().use {
                        assertEquals(2, it.totalRows)
                        it.next()
                        assertEquals(0L, it.getLong("id"))
                        assertEquals("foo", it.getString("name"))
                        assertEquals(byteArrayOf(9, 1, 1).toList(), it.getBytes("data_b").toList())

                        it.next()
                        assertEquals(1L, it.getLong("id"))
                        assertEquals("bar", it.getString("name"))
                        assertEquals(byteArrayOf(3, 5, 7).toList(), it.getBytes("data_b").toList())
                    }
            }
        }
    }

    @Test
    fun testBooleanData() {
        runBlocking {
            ds.connection().use { conn ->
                conn.statement("insert into testing (id, name, truth) VALUES ($1, $2, $3), ($4, $5, $6) returning *;")
                    .setLong(1, 0L)
                    .setString(2, "foo")
                    .setBool(3, false)
                    .setLong(4, 1L)
                    .setString(5, "bar")
                    .setBool(6, true)
                    .executeReturning().use {
                        assertEquals(2, it.totalRows)
                        it.next()
                        assertEquals(0L, it.getLong("id"))
                        assertEquals("foo", it.getString("name"))
                        assertFalse(it.getBoolean("truth"))

                        it.next()
                        assertEquals(1L, it.getLong("id"))
                        assertEquals("bar", it.getString("name"))
                        assertTrue(it.getBoolean("truth"))
                    }
            }
        }
    }
}
