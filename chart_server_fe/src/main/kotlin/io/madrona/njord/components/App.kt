package io.madrona.njord.components

import react.Props
import react.createElement
import react.dom.*
import react.fc
import react.router.Route
import react.router.Routes

val App = fc<Props> {
    div {
        NavBar()
        Routes {
            Route {
                attrs.index = true
                attrs.element = createElement(Chart)
            }
            Route {
                attrs.path = "/v1/app"
                attrs.element = createElement(Chart)
            }
            Route {
                attrs.path = "/v1/app/about"
                attrs.element = createElement(About)
            }
        }
    }
}
