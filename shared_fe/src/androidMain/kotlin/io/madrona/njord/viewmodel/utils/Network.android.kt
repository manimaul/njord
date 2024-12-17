package io.madrona.njord.viewmodel.utils

actual suspend inline fun <reified T> Network.get(
    api: String,
    params: Map<String, String>?
): NetworkResponse<T> {
    TODO("Not yet implemented")
}

actual suspend inline fun <reified T, reified R> Network.post(
    api: String,
    item: T,
    params: Map<String, String>?
): NetworkResponse<R> {
    TODO("Not yet implemented")
}

actual suspend fun Network.delete(
    api: String,
    params: Map<String, String>
): NetworkResponse<Unit> {
    TODO("Not yet implemented")
}