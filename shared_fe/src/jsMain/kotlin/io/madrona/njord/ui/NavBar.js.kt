package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.viewmodel.routeViewModel
import org.jetbrains.compose.web.dom.*

@Composable
actual fun NavBar() {
    val state by routeViewModel.flow.collectAsState()
    Nav(attrs = {
        classes(
            "navbar",
            "navbar-expand-lg",
            "bg-body-tertiary",
            "sticky-top",
            "border-body",
            "border-bottom"
        )
        attr("data-bs-theme", "dark")
    }) {
        Div(attrs = { classes("container-fluid") }) {
            A(attrs = { classes("navbar-brand") }, href = "#") {
                Text("Njord")
            }
            Button(attrs = {
                classes("navbar-toggler")
                attr("data-bs-toggle", "collapse")
                attr("data-bs-target", "#navbarNavDropdown")
                attr("aria-controls", "navbarNavDropdown")
                attr("aria-expanded", "false")
                attr("aria-label", "Toggle navigation")
            }) {
                Span(attrs = { classes("navbar-toggler-icon") }) { }
            }
            Div(attrs = {
                id("navbarNavDropdown")
                classes("collapse", "navbar-collapse")
            }) {
                Ul(attrs = { classes("navbar-nav") }) {
                    state.navBarRoutes.forEach { routing ->
                        Li(attrs = { classes("nav-item") }) {
                            Button(attrs = {
                                attr("data-bs-toggle", "collapse")
                                attr("data-bs-target", "#navbarNavDropdown")
                                if (routing.route == state.current.route) {
                                    classes("nav-link", "active")
                                } else {
                                    classes("nav-link")
                                }
                                onClick {
                                    routeViewModel.pushRoute(routing.pathAndParams())
                                }
                            }) {
                                Text(routing.route.title)
                            }
                        }
                    }
                    Li(attrs = { classes("nav-item") }) {
                        Button(attrs = {
                            classes("nav-link")
                            onClick {
                                println("todo")
                            }
                        }) {
                            Text("Admin")
                        }
                    }
                }
            }
        }
    }
}
