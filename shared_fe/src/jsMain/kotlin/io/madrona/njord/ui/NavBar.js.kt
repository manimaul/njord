package io.madrona.njord.ui

import androidx.compose.runtime.*
import io.madrona.njord.model.Depth
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.model.colorName
import io.madrona.njord.model.mode
import io.madrona.njord.viewmodel.NavBarViewModel
import io.madrona.njord.viewmodel.adminViewModel
import io.madrona.njord.viewmodel.chartViewModel
import io.madrona.njord.viewmodel.routeViewModel
import io.madrona.njord.viewmodel.utils.Complete
import io.madrona.njord.viewmodel.utils.Fail
import io.madrona.njord.viewmodel.utils.Loading
import io.madrona.njord.viewmodel.utils.Uninitialized
import org.jetbrains.compose.web.dom.*

@Composable
fun <T> NavDropdown(
    selected: T,
    title: (T) -> String,
    options: List<T>,
    callback: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    fun expand() = if (expanded) "show" else "hide"
    Div(attrs = {
        onClick {
            expanded = !expanded
        }
        classes("nav-item", "dropdown", expand())
    }) {
        A(href = "#", attrs = {
            classes("dropdown-toggle", "nav-link")
            tabIndex(0)
            attr("aria-expanded", "$expanded")
            attr("role", "button")
        }) {
            Text(title(selected))
        }
        Div(attrs = {
            classes("dropdown-menu", expand())
            attr("aria-labelledby", "basic-nav-dropdown")
            attr("data-bs-popper", "static")
        }) {
            options.forEachIndexed { i, option ->
                A(href = "#", attrs = {
                    onClick {
                        callback(option)
                    }
                    attr("data-rr-ui-dropdown-item", "")
                    classes("dropdown-item")
                    tabIndex(i)
                }) { Text("$option") }

            }
        }

    }
}

@Composable
actual fun NavBar() {
    val viewModel: NavBarViewModel = remember { NavBarViewModel() }
    val state by viewModel.flow.collectAsState()
    val routeState by routeViewModel.flow.collectAsState()
    val chartState by chartViewModel.flow.collectAsState()
    val adminState by adminViewModel.flow.collectAsState()
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
                    routeState.navBarRoutes.forEach { routing ->
                        Li(attrs = { classes("nav-item") }) {
                            Button(attrs = {
                                attr("data-bs-toggle", "collapse")
                                attr("data-bs-target", "#navbarNavDropdown")
                                if (routing.route == routeState.current.route) {
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
                    NavDropdown(chartState.depth, { "Depths: $it" }, Depth.entries.toList()) {
                        chartViewModel.setDepth(it)
                    }
                    NavDropdown(chartState.theme, { "Theme: ${it.mode()}" }, ThemeMode.entries.toList()) {
                        chartViewModel.setTheme(it)
                    }
                    state.customColors.value?.let { cc ->
                        NavDropdown(chartState.theme.colorName(), { "Colors: $it" }, cc) {
                            chartViewModel.setCustomColor(it)
                        }
                    }
                }

            }
        }

        when (adminState.adminSignature) {
            is Complete -> {
                Button(attrs = {
                    classes("btn", "btn-danger", "btn-sm")
                    onClick {
                        adminViewModel.logout()
                    }
                }) {
                    Text("Logout")
                }
            }

            is Loading -> {}
            is Fail,
            Uninitialized -> {
                Button(attrs = {
                    classes("btn", "btn-info", "btn-sm")
                    onClick {
                        adminViewModel.login()
                    }
                }) {
                    Text("Login")
                }
            }
        }
    }
}
