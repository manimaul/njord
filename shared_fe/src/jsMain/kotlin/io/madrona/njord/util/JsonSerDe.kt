package io.madrona.njord.util

import kotlinx.serialization.json.Json

val json = Json {
    useArrayPolymorphism = true
    isLenient = true
}
