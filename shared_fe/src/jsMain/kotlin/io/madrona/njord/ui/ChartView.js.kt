package io.madrona.njord.ui

import androidx.compose.runtime.*
import io.madrona.njord.js.MapLibre
import io.madrona.njord.js.mapLibreArgs
import io.madrona.njord.viewmodel.chartViewModel
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.web.dom.*

@Composable
actual fun ChartView() {
    val state by chartViewModel.flow.collectAsState()
    Div(attrs = { classes("Wrap", "Warning", "bg-danger", "text-white") }) {
        Text("EXPERIMENTAL! - NOT FOR NAVIGATION")
    }
    Div(
        attrs = {
            classes("Fill")
            ref { element ->
                println("creating map view")
                chartViewModel.controller.mapView = MapLibre.Map(
                    mapLibreArgs(element, chartViewModel.flow.value)
                )
                object : DisposableEffectResult {
                    override fun dispose() {
                        println("destroying map view")
                        chartViewModel.controller.mapView?.remove()
                        chartViewModel.controller.mapView = null
                    }
                }
            }
        })
    val showHide = chartViewModel.flow.map { it.query.isNotEmpty() }
    Modal(
        title = "Chart Query",
        onClose = { chartViewModel.clearQuery() },
        showHideFlow = showHide
    ) {
        ChartQuery(state.query)
    }
}

