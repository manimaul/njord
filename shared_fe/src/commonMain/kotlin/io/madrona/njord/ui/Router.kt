package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.viewmodel.Route
import io.madrona.njord.viewmodel.RouteViewModel
import io.madrona.njord.viewmodel.routeViewModel

@Composable
fun Router(viewModel: RouteViewModel = routeViewModel) {
    val state by viewModel.flow.collectAsState()
    AppBox {
        NavBar()
        when (state.current.route) {
            Route.Home -> Home()
            Route.NotFound -> NotFound()
        }
    }
}
