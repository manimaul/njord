package io.madrona.njord.viewmodel.utils

import io.madrona.njord.viewmodel.Route


class RouteMatcher private constructor(
    val route: Route,
    private val pathPattern: Regex,
    private val keywords: List<String>
) {
    fun matches(path: String): Boolean {
        return pathPattern.matches(path)
    }

    fun groups(path: String): Map<String, String>? {
        return pathPattern.find(path)?.let { result ->
            keywords.zip(result.destructured.toList()).toMap()
        }
    }

    companion object {
        private val keywordPattern = Regex("(:\\w+)")
        private fun compile(pattern: String, keywords: MutableList<String>): Regex {
            val regexPattern = StringBuilder()
            if (pattern == "/") {
                regexPattern.append("/")
            } else {
                val segments = pattern.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (segment in segments) {
                    if (segment != "") {
                        regexPattern.append("/")
                        if (keywordPattern.matches(segment)) {
                            val keyword = segment.substring(1)
                            regexPattern
                                .append("(?<")
                                .append(keyword)
                                .append(">[^/]*)")
                            keywords.add(keyword)
                        } else {
                            regexPattern.append(segment)
                        }
                    }
                }
            }
            regexPattern.append("[/]?")
            return Regex(regexPattern.toString())
        }

        fun build(route: Route): RouteMatcher {
            val keywords = mutableListOf<String>()
            val regex = compile(route.pathPattern, keywords)
            return RouteMatcher(route, regex, keywords)
        }
    }
}
