package io.madrona.njord.ext

fun String.mimeType() = when(extToken()) {
    "png" -> "image/png"
    "pbf" -> "application/x-protobuf"
    "svg" -> "image/svg+xml"
    "json" -> "application/json"
    "css" -> "text/css"
    "txt", "html" -> "text/html"
    "js" -> "text/javascript"
    else -> null
}

fun String.extToken() = lastIndexOf('.')
        .takeIf {
            it in 1 until length - 1
        }?.let {
            substring(it + 1).lowercase()
        }

fun String.intRange() : IntRange {
    var left: Int? = null
    var right: Int? = null
    val i = indexOf(',')
    if (i > 1 && length > 2 && get(0) == '[' && get(length - 1) == ']'){
        //inclusive, exclusive
        left = substring(1, i).toIntOrNull()
        right = substring(i + 1, length - 1).toIntOrNull()
    }
    return letTwo(left, right) { l, r ->
        l..r
    } ?: IntRange.EMPTY
}