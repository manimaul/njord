package io.madrona.njord.components

import kotlinx.html.ButtonType
import react.Props
import react.dom.*
import react.fc
import react.router.dom.Link

val NavBar = fc<Props> {
    nav(classes = "navbar navbar-expand-lg navbar-dark bg-dark") {
        div(classes = "container-fluid") {
            Link {
                + "Njord"
                attrs.also {
                    it.className = "navbar-brand"
                    it.to = "/v1/app"
                }
            }
            button(classes = "navbar-toggler", type = ButtonType.button) {
                setProp("data-bs-toggle", "collapse")
                setProp("data-bs-target", "#navbarNavAltMarkup")
                setProp("aria-expanded", false)
                span(classes = "navbar-toggler-icon") {}
            }
            div(classes = "collapse navbar-collapse") {
                setProp("id", "navbarNavAltMarkup")
                div(classes = "navbar-nav") {
                    Link {
                        + "About"
                        attrs.also {
                            it.className = "navbar-text"
                            it.to = "/v1/app/about"
                        }
                    }
                }
            }
        }
    }
}
