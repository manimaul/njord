package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.mimeType
import io.madrona.njord.util.logger
import io.madrona.njord.util.resourceBytes

class StaticResourceContentHandler : KtorHandler {
    private val log = logger()
    override val route = "/v1/content/{content...}"

    override suspend fun handleGet(call: ApplicationCall) {
        val segments = call.parameters.getAll("content").orEmpty()
        val contentType = segments.contentType() ?: return call.respond(HttpStatusCode.NotFound)
        val resName = segments.resourceName() ?: return call.respond(HttpStatusCode.NotFound)
        resourceBytes(resName)?.let { data ->
            call.respondBytes(data, contentType)
        } ?: run {
            log.error("error finding resource '$resName'")
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

/**
 * The resource behind a `/v1/content/...` request, built from the path segments Ktor has already
 * percent-decoded. The segments must never be re-encoded: a font stack such as "Roboto Bold" is a
 * directory with a space in its name, and `www/fonts/Roboto%20Bold` matches nothing on disk.
 * Null for a path that leaves `www/`: [resourceBytes] confines the real path to the resources
 * directory, but `www/../config/application.json` is inside it, so `.` and `..` are refused here.
 */
fun List<String>.resourceName(): String? =
    takeIf { it.isNotEmpty() && it.none { s -> s.isEmpty() || s == "." || s == ".." } }
        ?.let { "www/" + it.joinToString("/") }

fun List<String>.contentType(): ContentType? =
    lastOrNull()?.mimeType()?.let { ContentType.parse(it) }
