package io.madrona.njord.viewmodel

import io.madrona.njord.viewmodel.utils.RouteMatcher

enum class Route(val pathPattern: String) {
    Home("/"),
    NotFound("/404")
}

expect fun queryString(): String?

data class QueryParams(
    val queryString: String? = queryString()
) {
    val values: Map<String, String?>? by lazy {
        println("url = $queryString")
        queryString?.let {
            val retVal = mutableMapOf<String, String?>()
            queryString.split('&').forEach { qp ->
                val pair = qp.split('=')
                if (pair.size == 1) {
                    retVal[pair[0]] = null
                } else if (pair.size == 2) {
                    retVal[pair[0]] = pair[1]
                }
            }
            retVal
        }
    }
}

data class Routing(
    val route: Route,
    val path: String,
    val args: Map<String, String>? = null,
    val params: QueryParams? = null,
) {

    fun pathAndParams() : String {
        return params?.queryString?.let {
            "$path?$it"
        } ?: path
    }

    companion object {

        private val matchers by lazy {
            Route.entries.map { RouteMatcher.build(it) }
        }

        fun from(path: String, params: QueryParams? = null): Routing {
            val queryParams = params ?: QueryParams()
            return matchers.firstOrNull {
                it.matches(path)
            }?.let {
                Routing(it.route, path, it.groups(path), queryParams)
            } ?: Routing(Route.NotFound, path, null, queryParams)
        }

        fun from(route: Route, params: QueryParams? = null): Routing {
            val queryParams = params ?: QueryParams()
            return Routing(route, route.pathPattern, null, queryParams)
        }
    }
}

expect fun currentRouting() : Routing
expect fun currentHref(): String
expect fun currentHrefQueryParam(key: String): List<String>
expect fun windowHistoryBack()

data class RouteState(
    val current: Routing = currentRouting(),
    val href: String = currentHref(),
    val canGoback: Boolean = false,
    val replace: Boolean = true,
) : VmState

expect fun RouteViewModel.initialize()

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
                pushRoute(Route.Home)
            }
        }
    }

    fun replaceRoute(path: String) {
        setState {
            copy(
                current = Routing.from(path),
                href = "", //window.location.href,
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
        } else {
            println("already at route path $path")
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
        } else {
            println("already at route $route")
        }
    }
}

val routeViewModel = RouteViewModel()
