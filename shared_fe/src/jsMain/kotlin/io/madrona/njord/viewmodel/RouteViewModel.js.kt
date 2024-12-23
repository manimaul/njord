package io.madrona.njord.viewmodel

import io.madrona.njord.routing.Routing
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.w3c.dom.url.URL

actual fun currentHref(): String {
    return window.location.href
}

actual fun RouteViewModel.initialize() {
    window.addEventListener("popstate", {
        replaceRoute(window.location.pathname)
    })

    launch {
        flow.collect {
            if (it.replace) {
                window.history.replaceState(null, it.current.route.name, it.current.pathAndParams())
            } else {
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