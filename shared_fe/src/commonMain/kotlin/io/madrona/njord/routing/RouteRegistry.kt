package io.madrona.njord.routing

class RouteRegistry<R : Route>(
    private val routes: List<R>,
    private val notFound: R,
) {
    private val matchers by lazy { routes.map { it to RouteMatcher.build(it) } }

    fun from(path: String, params: QueryParams? = null): Routing<R> {
        val queryParams = params ?: QueryParams()
        return matchers.firstOrNull {
            it.second.matches(path)
        }?.let {
            Routing(it.first, path, it.second.groups(path), queryParams)
        } ?: Routing(notFound, path, null, queryParams)
    }
}
