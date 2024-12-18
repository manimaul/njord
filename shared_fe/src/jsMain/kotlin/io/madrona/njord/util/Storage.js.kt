package io.madrona.njord.util

import kotlinx.browser.window
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val json = Json { useArrayPolymorphism = true }

actual inline fun <reified T> localStoreSet(item: T?) {
    T::class.simpleName?.let { key ->
        val value = item?.let {
            try {
                json.encodeToString(it)
            } catch (e: Exception) {
                println("error setting value for $key: $e")
                null
            }
        } ?: ""
        println("setting key: $key $value")
        window.localStorage.setItem(key, value)
    }
}

actual inline fun <reified T> localStoreGet(): T? {
    return T::class.simpleName?.let { key ->
        window.localStorage.getItem(key)?.takeIf { it.isNotBlank() }?.let { value ->
            try {
                json.decodeFromString(value)
            } catch (e: Exception) {
                println("error getting value for $key: $e")
                null
            }
        }
    }
}
