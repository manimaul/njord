package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.routing.NjordRoute
import io.madrona.njord.viewmodel.RouteViewModel
import io.madrona.njord.viewmodel.routeViewModel

@Composable
fun Router(viewModel: RouteViewModel<NjordRoute> = routeViewModel) {
    val state by viewModel.flow.collectAsState()
    AppBox {
        NavBar()
        when (state.current.route) {
            NjordRoute.About -> RouteContent { Home() }
            NjordRoute.NotFound -> RouteContent { NotFound() }
            NjordRoute.Enc -> ChartView()
            NjordRoute.ControlPanel -> RouteContent {
                state.current.args?.get("tab")?.let { tab ->
                    state.current.args?.get("path")?.let { path ->
                        ControlPanel(tab, path)
                    } ?: ControlPanel(tab, "")
                } ?: NotFound()
            }

            NjordRoute.Chart -> RouteContent {
                state.current.args?.get("name")?.let {
                    ChartInfoPage(it)
                } ?: NotFound()
            }
            NjordRoute.Layer -> RouteContent {
                state.current.args?.get("name")?.let { name ->
                    LayerPage(name)
                } ?: NotFound()
            }
        }
    }
}
