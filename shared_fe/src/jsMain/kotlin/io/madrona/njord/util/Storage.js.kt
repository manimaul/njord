package io.madrona.njord.util

import kotlinx.browser.window
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual inline fun <reified T> localStoreSet(item: T?) {
    T::class.simpleName?.let { key ->
        val value = item?.let { Json.encodeToString(it) } ?: ""
        println("setting key: $key $value")
        window.localStorage.setItem(key, value)
    }
}

actual inline fun <reified T> localStoreGet(): T? {
    return T::class.simpleName?.let { key ->
        window.localStorage.getItem(key)?.let { value ->
            try {
                Json.decodeFromString(value)
            } catch (e: Exception) {
                null
            }
        }
    }
}
