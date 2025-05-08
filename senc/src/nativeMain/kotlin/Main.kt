
fun main() {
    val s57 = S57(
        "/home/willard/source/njord/chart_server/src/jvmTest/data/US5WA22M/US5WA22M.000"
    )
    s57.layerNames.forEach {
        println("layer name $it")
    }
    val fc = s57.featureCount()
    println("feature count = $fc")

    val db = PgDb.login(
        host = "localhost",
        database = "s57server",
        port = 5432,
        user = "admin",
        password = "mysecretpassword"
    )

    db.query("SELECT name from charts LIMIT 3;").use { result ->
        while (result.next()) {
            val name = result.getString(0)
            println("name = $name")
        }
    }
}
