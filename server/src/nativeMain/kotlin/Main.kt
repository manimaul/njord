import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

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

fun main() {
    ChartServerApp().serve()
}

fun Application.njord() {
    install(ContentNegotiation) {
        json()
    }
    routing {
        get("/") {
            call.respondText(
                contentType = ContentType.Text.Plain,
                text = "Nord native server"
            )
        }
    }
}

