package io.madrona.njord.network

import io.madrona.njord.model.AboutJson

data class NetworkResponse<T>(
    val url: String? = null,
    val ok: Boolean,
    val body: T? = null,
    val status: Short? = null,
    val statusText: String? = null,
    val error: Exception? = null
)

fun <T, R> NetworkResponse<T>.map(mapper: (T) -> R) : NetworkResponse<R> {
    return body?.let {
        NetworkResponse(url, ok, mapper(it), status, statusText, error)
    } ?: NetworkResponse(url, ok, null, status, statusText, error)
}


internal expect suspend inline fun <reified T> Network.get(api: String, params: Map<String, String>? = null): NetworkResponse<T>
internal expect suspend inline fun <reified T, reified R> Network.post(api: String, item: T, params: Map<String, String>? = null): NetworkResponse<R>
internal expect suspend fun Network.delete(api: String, params: Map<String, String>): NetworkResponse<Unit>

object Network {
    suspend fun getAbout() : NetworkResponse<AboutJson> = get("about/version")
}
