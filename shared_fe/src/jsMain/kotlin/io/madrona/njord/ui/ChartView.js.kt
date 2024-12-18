package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffectResult
import io.madrona.njord.viewmodel.chartViewModel
import org.jetbrains.compose.web.dom.Div

@Composable
actual fun ChartView() {
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
