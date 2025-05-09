import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.basic
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.time.Duration.Companion.seconds

class ChartServerApp {
    fun serve() {
        embeddedServer(
            CIO,
            host = Singletons.config.host,
            port = Singletons.config.port,
            module = Application::njord
        ).start(wait = true)
    }
}


val db = PgDb.login(
    host = "localhost",
    database = "s57server",
    port = 5432,
    user = "admin",
    password = "mysecretpassword"
)

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

fun Application.njord() {
    install(ContentNegotiation) {
        json()
    }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    Singletons.genLog = log
    install(CORS) {
        Singletons.config.allowHosts.forEach {
            allowHost(it)
        }
        allowHeader(HttpHeaders.ContentType)
    }
    install(Authentication) {
        basic("auth-basic") {
            realm = "Admin"
            validate { credentials ->
                if (Singletons.config.adminUser == credentials.name && Singletons.config.adminPass == credentials.password) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
    routing {
        get("/") {
            db.query("SELECT value FROM meta WHERE key='version';").use { result ->
                if (result.next()) {
                    val value = result.getString(0);
                    call.respondText(
                        contentType = ContentType.Text.Plain,
                        text = "Nord native server - version = $value"
                    )
                } else {
                    call.respond(status = HttpStatusCode.InternalServerError, "error")
                }
            }
        }
    }
}

