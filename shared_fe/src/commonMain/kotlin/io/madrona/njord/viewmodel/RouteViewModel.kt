package io.madrona.njord.viewmodel

import io.madrona.njord.routing.QueryParams
import io.madrona.njord.routing.Route
import io.madrona.njord.routing.Routing

expect fun currentRouting(): Routing
expect fun currentHref(): String
expect fun currentHrefQueryParam(key: String): List<String>
expect fun windowHistoryBack()
expect fun RouteViewModel.initialize()

data class RouteState(
    val current: Routing = currentRouting(),
    val href: String = currentHref(),
    val canGoback: Boolean = false,
    val replace: Boolean = true,
    val navBarRoutes: List<Routing> = listOf(
        Routing.from(Route.About),
        Routing.from(Route.Enc),
//        Route.controlPanel(),
    ),
)

class RouteViewModel : BaseViewModel<RouteState>(RouteState()) {

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
                pushRoute(Route.About)
            }
        }
    }

    fun replaceRoute(path: String) {
        setState {
            copy(
                current = Routing.from(path),
                href = currentHref(),
                replace = true,
            )
        }
    }

    fun pushRoute(path: String) {
        if (path != flow.value.current.path) {
            setState {
                copy(
                    current = Routing.from(path),
                    canGoback = true,
                    replace = false,
                )
            }
        }
    }

    fun pushRoute(route: Route) {
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

val routeViewModel = RouteViewModel()
