package io.madrona.njord.viewmodel

import io.madrona.njord.routing.Routing

actual fun currentHref(): String = ""

actual fun currentRouting(): Routing {
    return Routing.from("")
}

actual fun currentHrefQueryParam(key: String): List<String> {
    return emptyList()
}

actual fun windowHistoryBack() {}

actual fun RouteViewModel.initialize() {}