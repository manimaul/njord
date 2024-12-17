package io.madrona.njord.viewmodel.utils

import io.madrona.njord.model.LoginResponse
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.json

fun String.versionedApi(version: Int = 1, params: Map<String, String>? = null): String {
    val paramString = params?.let {
        StringBuilder().apply {
            it.onEachIndexed { index, entry ->
                if (index == 0) {
                    append('?')
                } else {
                    append('&')
                }
                append(entry.key)
                append('=')
                append(entry.value)
            }
        }.toString()
    } ?: ""
    return "/v$version/$this$paramString"
}

@OptIn(ExperimentalEncodingApi::class)
inline fun <reified T> localStoreGetEncoded(): String? {
    return T::class.simpleName?.let { key ->
        window.localStorage.getItem(key)?.takeIf { it.isNotBlank() }?.let { value ->
            try {
                Base64.encode(value.encodeToByteArray())
            } catch (e: Exception) {
                null
            }
        }
    }
}


fun token(): String {
    return localStoreGetEncoded<LoginResponse>() ?: "none"
}

actual suspend inline fun <reified T> Network.get(api: String, params: Map<String, String>?): NetworkResponse<T> {
    val response = window.fetch(
        api.versionedApi(params = params),
        RequestInit(
            method = "GET",
            headers = json(
                "Accept" to "application/json",
                "Authorization" to "Bearer ${token()}"
            ),
        )
    ).await()
    return response.networkResponse()
}

actual suspend inline fun <reified T, reified R> Network.post(api: String, item: T, params: Map<String, String>?): NetworkResponse<R> {
    val response = window.fetch(
        api.versionedApi(params = params),
        RequestInit(
            method = "POST",
            headers = json(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "Authorization" to "Bearer ${token()}"
            ),
            body = Json.encodeToJsonElement(item)
        )
    ).await()
    return response.networkResponse()
}

actual suspend fun Network.delete(api: String, params: Map<String, String>): NetworkResponse<Unit> {
    val response = window.fetch(
        api.versionedApi(params = params), RequestInit(
            method = "DELETE",
            headers = json("Authorization" to "Bearer ${token()}"),
        )
    ).await()
    return response.networkResponse(Unit)
}

suspend inline fun <reified T> Response.networkResponse(setBody: T? = null): NetworkResponse<T> {
    var body: T? = setBody
    var error: Exception? = null
    if (body == null) {
        try {
            body = Json.decodeFromString(text().await())
        } catch (e: Exception) {
            error = e
        }
    }
    return NetworkResponse(url, ok, body, status, statusText, error)
}
