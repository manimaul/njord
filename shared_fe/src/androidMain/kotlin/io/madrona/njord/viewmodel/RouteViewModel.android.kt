package io.madrona.njord.viewmodel

actual fun queryString(): String? = null
actual fun currentHref(): String = ""

actual fun currentRouting(): Routing {
    return Routing.from("")
}

actual fun currentHrefQueryParam(key: String): List<String> {
    return emptyList()
}

actual fun windowHistoryBack() {}

actual fun RouteViewModel.initialize() {}