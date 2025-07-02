import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class PgPreparedStatementTest {

    @Test
    fun testParamCount() {
        val ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)

        runBlocking {
            ds.connection().use {
                {
                    val statement = it.prepareStatement("SELECT name from charts LIMIT ?;") as PgStatement
                    assertEquals(1, statement.parameters)
                }
                {
                    val statement = it.prepareStatement("SELECT name from charts WHERE name=$1 LIMIT $2;") as PgStatement
                    assertEquals(2, statement.parameters)
                }
                {
                    val statement = it.prepareStatement("SELECT name from charts LIMIT $1;") as PgStatement
                    assertEquals(1, statement.parameters)
                }
            }
        }
    }

    @Test
    fun testPreparedStatementExists() {
        val ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)
        val exists = runBlocking {
            ds.connection().use { conn ->
                conn.prepareStatement("SELECT name from charts LIMIT 3;").let {
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
        val ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)

        val chartNames = runBlocking {
            ds.connection().use {
                it.prepareStatement("SELECT name from charts LIMIT 3;")
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

        val exists = runBlocking {
            ds.connection().prepareStatement("SELECT name from charts LIMIT 3;").let {
                val ps = (it as PgPreparedStatement)
                ps.preparedStatementExists()
            }
        }
        assertTrue(exists)
        ds.close()
    }

    @Test
    fun testPreparedStatementParams() {
        val ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)

        val chartNames = runBlocking {
            ds.connection().use {
                it.prepareStatement("SELECT name from charts LIMIT $1;")
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
        ds.close()
    }
}
