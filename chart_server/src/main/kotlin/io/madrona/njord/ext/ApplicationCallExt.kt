package io.madrona.njord.ext

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Respond using kotlinx-serialization-json rather than jackson.
 */
suspend inline fun <reified T : Any> ApplicationCall.respondJson(message: T) = respondText(
    Json.encodeToString(message),
    ContentType.Application.Json
)
