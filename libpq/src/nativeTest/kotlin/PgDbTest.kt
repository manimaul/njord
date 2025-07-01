import kotlin.test.Test
import kotlin.test.assertEquals


class PgDbTest {

    @Test
    fun testLoginExecQuery() {
        val db = PgDb.login(
            host = "localhost",
            database = "s57server",
            port = 5432,
            user = "admin",
            password = "mysecretpassword"
        )
        val chartNames = db.query("SELECT name from charts LIMIT 3;").use { result ->
            val names = mutableListOf<String>()
            while (result.next()) {
                val name = result.getString(0)
                names.add(name)
            }
            names
        }
        assertEquals(
            listOf(
                "US2WC03M.000",
                "US5WA22M.000",
                "US3WA46M.000",
            ), chartNames
        )
        assertEquals(3L,db.execute("SELECT * from charts LIMIT 3;"))
    }
}
