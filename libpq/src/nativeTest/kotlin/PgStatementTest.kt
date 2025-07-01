import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals


class PgStatementTest {

    @Test
    fun testStatement() {
        val ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)

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
        ds.close()
    }

    @Test
    fun testStatementParams() {
        val ds = PgDataSource("postgresql://admin:mysecretpassword@localhost:5432/s57server", 1)
        assertEquals(1, ds.readyCount)

        val chartNames = runBlocking {
            ds.connection().use {
                it.statement("SELECT name from charts LIMIT $1;")
                    .setInt(0, 3)
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
