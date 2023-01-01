package io.madrona.njord.ext

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons

/**
 * Respond using kotlinx-serialization-json rather than jackson.
 */
suspend inline fun <reified T : Any> ApplicationCall.respondJson(message: T) = respondText(
    Singletons.objectMapper.writeValueAsString(message),
    ContentType.Application.Json
)
