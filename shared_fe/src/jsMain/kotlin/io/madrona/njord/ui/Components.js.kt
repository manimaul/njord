package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.remember
import io.madrona.njord.viewmodel.ChartViewModel
import io.madrona.njord.viewmodel.utils.Fail
import org.jetbrains.compose.web.dom.Div

@Composable
actual fun LoadingSpinner() {
}

@Composable
actual fun <A> ErrorDisplay(event: Fail<A>, function: () -> Unit) {
}

@Composable
actual fun ChartView() {
    val viewModel = remember { ChartViewModel() }
    Div(
        attrs = {
            classes("Fill")
            ref { element ->
                println("creating map view")
                viewModel.controller.mapView = MapLibre.Map(
                    mapLibreArgs(element, viewModel.flow.value.location)
                )
                object : DisposableEffectResult {
                    override fun dispose() {
                        println("destroying map view")
                        viewModel.controller.mapView = null
                    }
                }
            }
        })
}