package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.remember
import io.madrona.njord.viewmodel.ChartViewModel
import io.madrona.njord.viewmodel.utils.Fail
import org.jetbrains.compose.web.ExperimentalComposeWebSvgApi
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.svg.*
import kotlin.math.PI

@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
actual fun LoadingSpinner() {

    val size = 50f
    val color = "black"
    val center = size / 2f
    val stroke = size / 10f
    val radius = center - stroke
    val amt = (2f * PI * radius) * .8
    Svg(attrs = {
        width(size)
        height(size)
    }) {
        Defs {
            LinearGradient("grad") {
                Stop(attrs = {
                    attr("offset", "0%")
                    attr("stop-color", color)
                })
                Stop(attrs = {
                    attr("offset", "100%")
                    attr("stop-color", color)
                    attr("stop-opacity", "0")
                })
            }
        }
        Circle(center, center, radius, attrs = {
            fill("none")
            attr("stroke", "url(#grad)")
            attr("stroke-width", "$stroke")
            attr("stroke-dasharray", "$amt")
            attr("stroke-linecap", "round")
        }) {
            AnimateTransform(attrs = {
                attributeName("transform")
                attr("attributeType", "XML")
                attr("type", "rotate")
                attr("repeatCount", "indefinite")
                attr("from", "0, $center, $center")
                attr("to", "360, $center, $center")
                attr("dur", "2s")
            })
        }
    }
}

@Composable
actual fun <A> ErrorDisplay(event: Fail<A>, function: () -> Unit) {
    H1 {
        Text("Something went wrong")
    }
    P {
        B { Text(event.message) }
    }
    Button(attrs = {
        classes("btn", "btn-danger")
        onClick { function() }
    }) {
        Text("OK")
    }
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