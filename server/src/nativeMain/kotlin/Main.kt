import io.ktor.http.ContentDisposition.Companion.File
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json.Default.decodeFromString

class ChartServerApp {
    fun serve() {
        embeddedServer(
            CIO,
            host = "0.0.0.0",
            port = 9000,
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
        val contents = it.readContents()
        Singletons.config = decodeFromString<ChartsConfig>(contents)
        println("config found $contents")
        ChartServerApp().serve()
    } ?: run {
        println("Please include argument of configuration path")
    }
}

fun Application.njord() {
    install(ContentNegotiation) {
        json()
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

