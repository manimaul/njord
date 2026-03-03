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
