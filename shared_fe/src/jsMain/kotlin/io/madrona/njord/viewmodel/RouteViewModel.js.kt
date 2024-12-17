package io.madrona.njord.viewmodel

import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.w3c.dom.url.URL

actual fun queryString(): String? {
    val url = window.location.href
    return url.lastIndexOf('?').takeIf { it > 0 }?.let { qs ->
        url.substring(qs + 1)
    }
}

actual fun currentHref(): String {
    return window.location.href
}

actual fun RouteViewModel.initialize() {
    window.addEventListener("popstate", {
        replaceRoute(window.location.pathname)
    })

    launch {
        flow.collect {
            println("state = $it")
            if (it.replace) {
                println("replacing history state ${it.current.path}")
                window.history.replaceState(null, it.current.route.name, it.current.pathAndParams())
            } else {
                println("pushing history state ${it.current.path}")
                window.history.pushState(null, it.current.route.name, it.current.pathAndParams())
            }
        }
    }
}

actual fun currentRouting(): Routing {
    return Routing.from(window.location.pathname)
}

actual fun currentHrefQueryParam(key: String): List<String> {
    return URL(window.location.href).searchParams.getAll(key).toList()
}

actual fun windowHistoryBack() {
    window.history.back()
}