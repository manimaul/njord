package io.madrona.njord.routing


internal class RouteMatcher private constructor(
    private val pathPattern: Regex,
    private val keywords: List<String>
) {
    fun matches(path: String): Boolean {
        val p = if (path.endsWith('#')) {
            path.substring(0, path.length - 1)
        } else {
            path
        }
        return if (p.endsWith('/')) {
            pathPattern.matches(p)
        } else {
            pathPattern.matches("$p/")
        }

    }

    fun groups(path: String): Map<String, String> {
        val i = path.lastIndexOf("?").takeIf { it != -1 } ?: path.length
        val p = if (i > 0 && path[i - 1] != '/') {
            path.substring(0, i) + "/"
        } else {
            path.substring(0, i)
        }
        return pathPattern.find(p)?.let { result ->
            keywords.zip(result.destructured.toList()).toMap().mapValues {
                it.value.removeSuffix("/")
            }
        } ?: emptyMap()
    }

    companion object {
        private val keywordPattern = Regex("(:\\w+|:\\*\\w+)")

        fun compile(pattern: String, keywords: MutableList<String>): Regex {
            val regexPattern = StringBuilder()

            if (pattern == "/") {
                regexPattern.append("/")
            } else {
                val segments = pattern.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (segment in segments) {
                    if (segment != "") {
                        regexPattern.append("/")
                        if (keywordPattern.matches(segment)) {
                            var keyword = segment.substring(1)
                            if (keyword.indexOf("*") == 0) {
                                keyword = keyword.substring(1)
                                regexPattern.append("(?<").append(keyword).append(">.*)")
                            } else {
                                regexPattern.append("(?<").append(keyword).append(">[^/]*)")
                            }
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

        fun build(path: String): RouteMatcher {
            val keywords = mutableListOf<String>()
            val regex = compile(path, keywords)
            return RouteMatcher(regex, keywords)
        }

        fun build(route: Route): RouteMatcher {
            return build(route.pathPattern)
        }
    }
}
