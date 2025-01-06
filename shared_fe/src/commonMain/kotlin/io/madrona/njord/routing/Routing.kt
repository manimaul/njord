package io.madrona.njord.routing

data class Routing(
    val route: Route,
    val path: String,
    val args: Map<String, String>? = null,
    val params: QueryParams? = null,
) {

    fun pathAndParams(): String {
        return params?.queryString?.let {
            "$path?$it"
        } ?: path
    }

    val pathSegments by lazy {
        path.split('/').filter { it.isNotBlank() }
    }

    companion object {

        private val matchers by lazy {
            Route.entries.map { it to RouteMatcher.build(it) }
        }

        fun from(path: String, params: QueryParams? = null): Routing {
            val queryParams = params ?: QueryParams()
            return matchers.firstOrNull {
                it.second.matches(path)
            }?.let {
                Routing(it.first, path, it.second.groups(path), queryParams)
            } ?: Routing(Route.NotFound, path, null, queryParams)
        }

        fun from(route: Route): Routing {
            return Routing(route, route.pathPattern)
        }
    }
}
