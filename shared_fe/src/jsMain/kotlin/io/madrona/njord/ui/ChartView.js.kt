package io.madrona.njord.ui

import androidx.compose.runtime.*
import io.madrona.njord.js.MapLibre
import io.madrona.njord.js.mapLibreArgs
import io.madrona.njord.viewmodel.chartViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
actual fun ChartView() {
    val state by chartViewModel.flow.collectAsState()
    remember {
        CoroutineScope(Dispatchers.Default).apply{
            launch {
                chartViewModel.flow.map { it.query }.collect {
                    if (it.isNotEmpty()) {
                        modalViewModel.show()
                    } else {
                        modalViewModel.hide()
                    }
                }

            }
        }
    }
    Div(attrs = { classes("Wrap", "Warning", "bg-danger", "text-white") }) {
        Text("EXPERIMENTAL! - NOT FOR NAVIGATION")
    }
    Div(
        attrs = {
            classes("Fill")
            ref { element ->
                chartViewModel.controller.mapView = MapLibre.Map(
                    mapLibreArgs(element, chartViewModel.flow.value)
                )
                object : DisposableEffectResult {
                    override fun dispose() {
                        chartViewModel.controller.mapView?.remove()
                        chartViewModel.controller.mapView = null
                    }
                }
            }
        })
    Modal(
        title = "Chart Query",
        onClose = { chartViewModel.clearQuery() },
    ) {
        ChartQuery(state.query)
    }
}

