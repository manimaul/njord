import io.madrona.njord.ChartServerApp
import io.madrona.njord.resources
import io.madrona.njord.util.File
import kotlinx.cinterop.ExperimentalForeignApi

//class ChartServerApp {
//    fun serve() {
//        embeddedServer(
//            CIO,
//            host = Singletons.config.host,
//            port = Singletons.config.port,
//            module = Application::njord
//        ).start(wait = true)
//    }
//}


//val db = PgDb.login(
//    host = "localhost",
//    database = "s57server",
//    port = 5432,
//    user = "admin",
//    password = "mysecretpassword"
//)

@OptIn(ExperimentalForeignApi::class)
fun main(args: Array<String>) {
    args.takeIf { it.isNotEmpty() }?.let {
        File(it[0])
    }?.takeIf { it.exists() }?.let {
        resources = it.path.toString()
        println("starting server with resources path $resources")
        ChartServerApp().serve()
    } ?: run {
        println("Path to resources directory was not supplied")
    }
}

//fun Application.njord() {
//    install(ContentNegotiation) {
//        json()
//    }
//    install(WebSockets) {
//        pingPeriod = 15.seconds
//        timeout = 15.seconds
//        maxFrameSize = Long.MAX_VALUE
//        masking = false
//    }
//    Singletons.genLog = log
//    install(CORS) {
//        Singletons.config.allowHosts.forEach {
//            allowHost(it)
//        }
//        allowHeader(HttpHeaders.ContentType)
//    }
//    install(Authentication) {
//        basic("auth-basic") {
//            realm = "Admin"
//            validate { credentials ->
//                if (Singletons.config.adminUser == credentials.name && Singletons.config.adminPass == credentials.password) {
//                    UserIdPrincipal(credentials.name)
//                } else {
//                    null
//                }
//            }
//        }
//    }
//    routing {
//        get("/") {
//            db.query("SELECT value FROM meta WHERE key='version';").use { result ->
//                if (result.next()) {
//                    val value = result.getString(0);
//                    call.respondText(
//                        contentType = ContentType.Text.Plain,
//                        text = "Nord native server - version = $value"
//                    )
//                } else {
//                    call.respond(status = HttpStatusCode.InternalServerError, "error")
//                }
//            }
//        }
//    }
//}

