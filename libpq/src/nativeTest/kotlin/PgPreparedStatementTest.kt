import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class PgPreparedStatementTest {

    lateinit var ds: PgDataSource

    @BeforeTest
    fun beforeEach() {
        ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)
        runBlocking {
            ds.connection().use { conn ->
                conn.statement(testDbSql)
                    .execute()
            }
        }
    }

    @AfterTest
    fun afterEach() {
        ds.close()
    }

    @Test
    fun testParamCount() {
        runBlocking {
            ds.connection().use { conn ->
                (conn.prepareStatement("SELECT name from testing LIMIT $1;") as PgStatement).also { statement ->
                    assertEquals(1, statement.parameters)
                }
                (conn.prepareStatement("SELECT name from testing WHERE name=$1 LIMIT $2;") as PgStatement).also { statement ->
                    assertEquals(2, statement.parameters)
                }
            }
        }
    }

    @Test
    fun testPreparedStatementExists() {
        val exists = runBlocking {
            ds.connection().use { conn ->
                conn.prepareStatement("SELECT name from testing LIMIT 3;").let {
                    val ps = (it as PgPreparedStatement)
                    if (!ps.preparedStatementExists()) {
                        ps.prepare("mycursor")
                    }
                    ps.preparedStatementExists()
                }
            }
        }
        assertTrue(exists)

        ds.close()
    }

    @Test
    fun testPreparedStatement() {
        val names = runBlocking {
            ds.connection().use { conn ->

                conn.statement(
                    "insert into testing VALUES (1, 'foo'), (2, 'bar'), (3, 'baz');"
                ).execute()

                conn.prepareStatement("SELECT name from testing LIMIT 3;")
                    .executeQuery().use { result ->
                        val names = mutableListOf<String>()
                        while (result.next()) {
                            val name = result.getString(1)
                            names.add(name)
                        }
                        names.toList()
                    }
            }
        }
        assertEquals(
            listOf(
                "foo",
                "bar",
                "baz",
            ), names
        )

        val exists = runBlocking {
            ds.connection().prepareStatement("SELECT name from testing LIMIT 3;").let {
                val ps = (it as PgPreparedStatement)
                ps.preparedStatementExists()
            }
        }
        assertTrue(exists)
    }

    @Test
    fun testInvalidPreparedStatementParams() {
        runBlocking {
            ds.connection().use { conn ->
                val statement = conn.prepareStatement("SELECT name from does_not_exist LIMIT $1;")
                    .setInt(1, 3)
                assertFails {
                    statement.executeQuery()
                }

                val count = conn.statement(
                    "insert into testing VALUES (1, 'foo'), (2, 'bar'), (3, 'baz');"
                ).execute()
                assertEquals(3, count)
            }
        }
    }

    @Test
    fun testPreparedStatementParams() {
        val names = runBlocking {
            ds.connection().use { conn ->
                conn.statement(
                    "insert into testing VALUES (1, 'foo'), (2, 'bar'), (3, 'baz');"
                ).execute()

                conn.prepareStatement("SELECT name from testing LIMIT $1;")
                    .setInt(1, 3)
                    .executeQuery().use { result ->
                        val names = mutableListOf<String>()
                        while (result.next()) {
                            val name = result.getString(1)
                            names.add(name)
                        }
                        names.toList()
                    }
            }
        }

        assertEquals(
            listOf(
                "foo",
                "bar",
                "baz",
            ), names
        )
    }

    @Test
    fun testUpdate() {
        runBlocking {
            ds.connection().use { conn ->
                val result =
                    conn.statement("insert into testing VALUES (1, 'bar'), (2, 'baz') returning *;")
                        .executeReturning()
                assertEquals(2, result.totalRows)

                assertTrue(result.next())
                assertEquals(1L, result.getLong("id"))
                assertEquals("bar", result.getString("name"))

                assertTrue(result.next())
                assertEquals(2L, result.getLong(1))
                assertEquals("baz", result.getString(2))

                assertFalse(result.next())
            }
        }
    }

    @Test
    fun testUpdateParams() {
        runBlocking {
            ds.connection().use { conn ->
                println("creating statement")

                val statement =
                    conn.statement("insert into testing (id, name) VALUES ($1, $2), ($3, $4) returning *;")
                        .setLong(1, 1)
                        .setString(2, "bar")
                        .setLong(3, 2)
                        .setString(4, "baz")
                val result = statement.executeReturning()
                assertEquals(2, result.totalRows)

                assertTrue(result.next())
                assertEquals(1L, result.getLong("id"))
                assertEquals("bar", result.getString("name"))

                assertTrue(result.next())
                assertEquals(2L, result.getLong(1))
                assertEquals("baz", result.getString(2))

                assertFalse(result.next())
            }
        }
    }


    @Test
    fun testJsonQuery() {
        runBlocking {
            ds.connection().use { conn ->
                println("creating statement")
                var i = 0
                println("json = ${encodeToString(KvString(key = "value"))}")
                conn.statement("insert into testing (id, name, json_b) VALUES ($1, $2, $3), ($4, $5, $6);")
                    .setLong(++i, 1)
                    .setString(++i, "bar")
                    .setAuto(++i, encodeToString(KvString(key = "value")))
                    .setLong(++i, 2)
                    .setString(++i, "baz")
                    .setAuto(++i, encodeToString(KvDouble(key = 0.3)))
                    .execute()

                conn.statement("select * from testing where json_b->>'key'='value';")
                    .executeQuery().use { result ->
                        assertEquals(1, result.totalRows, "1st query number of rows")
                        assertTrue(result.next(), "1st query has next")
                        assertEquals("bar", result.getString("name"))
                        assertEquals(1L, result.getLong("id"))
                        assertEquals(
                            expected = KvString(key = "value"),
                            actual = decodeFromString(result.getString("json_b"))
                        )
                        assertFalse(result.next(), "1st query no next")
                    }
                conn.statement("select * from testing where json_b->>'key'='0.3';")
                    .executeQuery().use { result ->
                        assertTrue(result.next(), "2nd query has next")
                        assertEquals(1, result.totalRows, "2nd query number of rows")
                        assertEquals("baz", result.getString("name"))
                        assertEquals(2L, result.getLong("id"))

                        assertEquals(
                            expected = KvDouble(key = 0.3),
                            actual = decodeFromString(result.getString("json_b"))
                        )

                        assertFalse(result.next(), "2nd query no next")
                    }
            }
        }
    }

    @Test
    fun testArrayQuery() {
        runBlocking {
            ds.connection().use { conn ->
                println("creating statement")
                var i = 0
                conn.statement("insert into testing (id, name, array_t) VALUES ($1, $2, $3), ($4, $5, $6);")
                    .setLong(++i, 1)
                    .setString(++i, "bar")
                    .setArray(++i, arrayOf("a", "b", "c"))

                    .setLong(++i, 2)
                    .setString(++i, "baz")
                    .setArray(++i, arrayOf("d", "e", "f")).execute()

                conn.statement("select * from testing where name='bar';")
                    .executeQuery().use { result ->
                        assertEquals(1, result.totalRows, "1st query rows")
                        assertTrue(result.next(), "1st query next")
                        println(result.keys)
                        assertEquals("bar", result.getString("name"))
                        assertEquals(1L, result.getLong("id"))
                        assertEquals(arrayOf("a", "b", "c").toList(), result.getArray("array_t").toList())
                        assertFalse(result.next(), "1st query next")
                    }
                conn.statement("select * from testing where name='baz';")
                    .executeQuery().use { result ->
                        assertEquals(1, result.totalRows, "2nd query rows")
                        assertTrue(result.next(), "2nd query next")
                        println(result.keys)
                        assertEquals("baz", result.getString("name"))
                        assertEquals(2L, result.getLong("id"))
                        assertEquals(arrayOf("d", "e", "f").toList(), result.getArray("array_t").toList())
                        assertFalse(result.next())
                    }
            }
        }
    }

    @Test
    fun testUnNestArrayQuery() {
        runBlocking {
            ds.connection().use { conn ->
                println("creating statement")
                var i = 0
                conn.statement("insert into testing VALUES ($1, $2, null, $3), ($4, $5, null, $6);")
                    .setLong(++i, 1)
                    .setString(++i, "bar")
                    .setArray(++i, arrayOf("a", "b", "c"))

                    .setLong(++i, 2)
                    .setString(++i, "baz")
                    .setArray(++i, arrayOf("d", "e", "f")).execute()

                conn.statement("select unnest(array_t) as element from testing where name='bar';")
                    .executeQuery().use { result ->
                        assertEquals(3, result.totalRows)
                        assertTrue(result.next())
                        assertEquals("a", result.getString("element"))
                        assertTrue(result.next())
                        assertEquals("b", result.getString("element"))
                        assertTrue(result.next())
                        assertEquals("c", result.getString("element"))
                        println(result.keys)
                        assertFalse(result.next())
                    }
            }
        }
    }
}
