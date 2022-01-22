package io.madrona.njord

import kotlinx.browser.window
import react.RBuilder
import react.dom.a

fun String.pathToFullUrl() : String {
    return "${window.location.protocol}//${window.location.host}${this}"
}

fun RBuilder.pathToA(path: String) {
    path.pathToFullUrl().also {
        a(href = it) { +it }
    }
}