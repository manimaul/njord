package io.madrona.njord

import io.madrona.njord.components.App
import kotlinx.browser.document
import kotlinx.css.*
import react.dom.render
import react.router.dom.BrowserRouter
import styled.injectGlobal

fun main() {
    //https://ktor.io/docs/css-dsl.html#use_css
    injectGlobal(
        CssBuilder(allowClasses = false).apply {
            body {
                height = 100.vh
                margin(0.px)
            }
        }
    )

    render(document.getElementById("root")!!) {
        BrowserRouter {
            App()
        }
    }
}
