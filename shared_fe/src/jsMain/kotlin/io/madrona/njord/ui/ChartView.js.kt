package io.madrona.njord.ui

import androidx.compose.runtime.*
import io.madrona.njord.viewmodel.chartViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
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
                chartViewModel.flow.map { it.query }.distinctUntilChanged().collect {
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
                chartViewModel.controller.createMapView(element)
                object : DisposableEffectResult {
                    override fun dispose() {
                        chartViewModel.controller.disposeMapView()
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

