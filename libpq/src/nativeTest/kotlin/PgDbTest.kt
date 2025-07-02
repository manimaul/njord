import kotlin.test.Test

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
        db.close()
    }
}
