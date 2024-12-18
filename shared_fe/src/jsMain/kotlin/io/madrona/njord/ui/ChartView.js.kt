package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffectResult
import io.madrona.njord.viewmodel.chartViewModel
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
actual fun ChartView() {
    Div(attrs = { classes("Wrap","Warning","bg-danger", "text-white") }) {
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
                        chartViewModel.controller.mapView = null
                    }
                }
            }
        })
}
