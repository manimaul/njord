package io.madrona.njord.viewmodel

import io.madrona.njord.routing.NjordRoute
import io.madrona.njord.routing.QueryParams
import io.madrona.njord.routing.Route
import io.madrona.njord.routing.RouteRegistry
import io.madrona.njord.routing.Routing

expect fun currentPath(): String
expect fun currentHref(): String
expect fun currentHrefQueryParam(key: String): List<String>
expect fun windowHistoryBack()
expect fun <R : Route> RouteViewModel<R>.initialize()

data class RouteState<R : Route>(
    val current: Routing<R>,
    val href: String = currentHref(),
    val canGoback: Boolean = false,
    val replace: Boolean = true,
)

class RouteViewModel<R : Route>(
    private val registry: RouteRegistry<R>,
    private val homeRoute: R,
) : BaseViewModel<RouteState<R>>(
    RouteState(current = registry.from(currentPath()))
) {

    fun getQueryParam(key: String): List<String> {
        return currentHrefQueryParam(key)
    }

    init {
        initialize()
    }

    override fun reload() {
    }

    fun goBackOrHome() {
        withState {
            if (it.canGoback) {
                windowHistoryBack()
            } else {
                pushRoute(homeRoute)
            }
        }
    }

    fun replaceRoute(routing: Routing<R>) {
        setState {
            copy(
                current = routing,
                href = currentHref(),
                replace = true,
            )
        }
    }

    fun replaceRoute(path: String) {
        replaceRoute(registry.from(path))
    }

    fun pushRoute(path: String) {
        if (path != flow.value.current.path) {
            setState {
                copy(
                    current = registry.from(path),
                    canGoback = true,
                    replace = false,
                )
            }
        }
    }

    fun pushRoute(route: R) {
        if (route.pathPattern != flow.value.current.route.pathPattern) {
            setState {
                copy(
                    current = Routing(route, route.pathPattern, null, QueryParams()),
                    canGoback = true,
                    replace = false,
                )
            }
        }
    }
}

val routeViewModel = RouteViewModel(NjordRoute.registry, NjordRoute.About)
