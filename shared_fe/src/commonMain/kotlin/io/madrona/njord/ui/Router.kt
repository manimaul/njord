package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.routing.Route
import io.madrona.njord.viewmodel.RouteViewModel
import io.madrona.njord.viewmodel.routeViewModel

@Composable
fun Router(viewModel: RouteViewModel = routeViewModel) {
    val state by viewModel.flow.collectAsState()
    AppBox {
        NavBar()
        when (state.current.route) {
            Route.About -> RouteContent { Home() }
            Route.NotFound -> RouteContent { NotFound() }
            Route.Enc -> ChartView()
            Route.ControlPanel -> RouteContent {
                state.current.args?.get("tab")?.let { tab ->
                    state.current.args?.get("path")?.let { path ->
                        ControlPanel(tab, path)
                    } ?: ControlPanel(tab, "")
                } ?: NotFound()
            }
        }
    }
}
