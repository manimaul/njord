package io.madrona.njord.util

import kotlinx.serialization.json.Json

val json = Json {
    ignoreUnknownKeys = true
    useArrayPolymorphism = true
    isLenient = true
}
