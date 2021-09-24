package io.madrona.njord.endpoints

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.mimeType
import io.madrona.njord.resourceBytes
import java.lang.StringBuilder
import java.net.URLDecoder

class StaticContentHandler : KtorHandler {
    override val route = "/v1/content/{content...}"

    override suspend fun handleGet(call: ApplicationCall) {
        call.parameters.getAll("content")?.fold(StringBuilder()) { acc, s ->
            acc.append('/').append(s)
        }?.let {
            runCatching { URLDecoder.decode(it.toString(), "UTF-8") }.getOrNull()
        }?.let { name ->
            name.mimeType()?.let {
                ContentType.parse(it)
            }?.let { contentType ->
                resourceBytes("/www/$name")?.let { data ->
                    call.respondBytes(data, contentType)
                }
            }
        } ?: call.respond(HttpStatusCode.NotFound)
    }
}