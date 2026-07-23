package io.madrona.njord.routing

data class Routing<R : Route>(
    val route: R,
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
        fun <R : Route> from(route: R): Routing<R> {
            return Routing(route, route.pathPattern)
        }
    }
}
