//
//fun main() {
//    val db = PgDb.login(
//        host = "localhost",
//        database = "s57server",
//        port = 5432,
//        user = "admin",
//        password = "mysecretpassword"
//    )
//
//    db.query("SELECT name from charts LIMIT 3;").use { result ->
//        while (result.next()) {
//            val name = result.getString(0)
//            println("name = $name")
//        }
//    }
//}
