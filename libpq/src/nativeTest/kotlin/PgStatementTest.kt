import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals


class PgStatementTest {

    lateinit var ds: PgDataSource

    @BeforeTest
    fun beforeEach() {
        //pgDebug = true
        ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)
        runBlocking {
            ds.connection().use { conn ->
                conn.statement("drop table if exists testing;")
                    .execute()
                conn.statement("create table if not exists testing (id bigserial primary key, name varchar unique not null);")
                    .execute()
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
            ds.connection().use {
                it.statement("SELECT name from charts LIMIT 3;")
                    .executeQuery().use { result ->
                        val names = mutableListOf<String>()
                        while (result.next()) {
                            val name = result.getString(0)
                            names.add(name)
                        }
                        names
                    }
            }
        }

        assertEquals(
            listOf(
                "US2WC03M.000",
                "US5WA22M.000",
                "US3WA46M.000",
            ), chartNames
        )
    }

    @Test
    fun testStatementParams() {
        val chartNames = runBlocking {
            ds.connection().use {
                it.statement("SELECT name from charts LIMIT $1;")
                    .setInt(1, 3)
                    .executeQuery().use { result ->
                        val names = mutableListOf<String>()
                        while (result.next()) {
                            val name = result.getString(0)
                            names.add(name)
                        }
                        names
                    }
            }
        }

        assertEquals(
            listOf(
                "US2WC03M.000",
                "US5WA22M.000",
                "US3WA46M.000",
            ), chartNames
        )
    }

    @Test
    fun testExecute() {
        runBlocking {
            ds.connection().use { conn ->
                val count = conn.statement("insert into testing VALUES (1, 'bar'), (2, 'baz');").execute()
                val stmt = conn.statement("insert into testing VALUES ($1, $2), ($3, $4);")
                assertEquals(4, (stmt as PgStatement).parameters)

                stmt.setLong(1, 3L)
                    .setString(2, "rab")
                    .setLong(3, 4L)
                    .setString(4, "zab")

                val countParms = stmt.execute()

                assertEquals(2, count)
                assertEquals(2, countParms)
            }
        }
    }
    @Test
    fun testExecute100Params() {
        runBlocking {
            ds.connection().use { conn ->
                var sql = "insert into testing VALUES "
                (1..99).forEach {
                    if (it % 2 == 1) {
                        sql += "(\$$it, \$${it + 1})${if (it == 99) ";" else ", "}"
                    }
                }
                println("creating statement for sql = '$sql'")
                val stmt = conn.statement(sql)
                assertEquals(100, (stmt as PgStatement).parameters)
                println("binding params")
                (1..99).forEach {
                    if (it % 2 == 1) {
                        stmt.setLong(it, 10000L +it)
                        stmt.setString(it+1, "value ${it+1}")
                    }
                }
                println("executing insert")
                stmt.execute()

                var count = 0
                println("creating select statement")
                val select = conn.statement("select * from testing;")
                println("executing select staement")
                select.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val id = resultSet.getLong(0)
                        val name = resultSet.getString(1)
                        println("result record id: $id value = $name")
                        count++
                    }
                }
                assertEquals(50, count )
            }

            ds.connection().use { conn ->
                var count = 0
                conn.prepareStatement("select * from testing;").executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val id = resultSet.getLong(0)
                        val name = resultSet.getString(1)
                        println("result record id: $id value = $name")
                        count++
                    }
                }
                assertEquals(50, count )
                count = 0
                conn.prepareStatement("select * from testing;").executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val id = resultSet.getLong(0)
                        val name = resultSet.getString(1)
                        println("result record id: $id value = $name")
                        count++
                    }
                }
                assertEquals(50, count )
            }
        }
    }
}
