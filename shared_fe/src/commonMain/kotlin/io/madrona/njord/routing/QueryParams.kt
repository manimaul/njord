package io.madrona.njord.routing

import io.madrona.njord.viewmodel.currentHref


fun queryString(): String? {
    val url = currentHref()
    return url.lastIndexOf('?').takeIf { it > 0 }?.let { qs ->
        url.substring(qs + 1)
    }
}

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
