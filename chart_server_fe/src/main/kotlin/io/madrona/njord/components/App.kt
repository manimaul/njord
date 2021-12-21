package io.madrona.njord.components

import io.madrona.njord.styles.AppRoutes
import react.Props
import react.createElement
import react.dom.*
import react.fc
import react.router.Route
import react.router.Routes

val App = fc<Props> {
    div {
        NavBar {}
        Routes {
            Route {
                attrs.index = true
                attrs.element = createElement(Chart)
            }
            Route {
                attrs.path = AppRoutes.home
                attrs.element = createElement(Chart)
            }
            Route {
                attrs.path = AppRoutes.about
                attrs.element = createElement(About)
            }
            Route {
                attrs.path = AppRoutes.control
                attrs.element = createElement(Control)
            }
            Route {
                attrs.path = AppRoutes.controlPage
                attrs.element = createElement(Control)
            }
        }
    }
}
