package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffectResult
import io.madrona.njord.viewmodel.utils.Fail
import org.jetbrains.compose.web.dom.Div

@Composable
actual fun LoadingSpinner() {
}

@Composable
actual fun <A> ErrorDisplay(event: Fail<A>, function: () -> Unit) {
}

object NoopDER : DisposableEffectResult {
    override fun dispose() {}
}


//external fun require(module: String): dynamic

@Composable
actual fun ChartView() {
//    require("maplibre-gl/dist/maplibre-gl.css")
    Div(
        attrs = {
            classes("Fill")
            ref {
                MapLibre.Map(mapLibreArgs(it))
                NoopDER
            }
        })
}