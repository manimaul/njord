package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.madrona.njord.routing.Route
import io.madrona.njord.viewmodel.*
import kotlinx.serialization.json.*
import org.jetbrains.compose.web.dom.*

private fun JsonElement.isEmpty() : Boolean{
    return when(this) {
        is JsonArray -> this.isEmpty()
        is JsonObject -> this.isEmpty()
        is JsonPrimitive -> this.content.isEmpty()
        JsonNull -> true
    }
}

@Composable
actual fun LayerPage(name: String) {
    val viewModel = remember { LayerPageViewModel(name) }
    val state by viewModel.flow.collectAsState()
    H1 {
        Text("Chart Locations for layer $name")
    }
    state.pages.complete(viewModel) { pages ->
        val items = pages.map { it.items }.flatten()
        B { Text("${items.size } feature(s) found") }
        Br()
        if (pages.lastOrNull()?.lastId != 0L) Button(attrs = {
            classes("btn", "btn-outline-success", "btn-sm")
            onClick {
                viewModel.nextPage()
            }
        }){
            Text("Load more")
        }
        Ol {
            items.forEach { item ->
                Li(attrs = {
                    classes("my-1")
                }) {
                    Text("${item.chartName} ${item.lat} ${item.lng} ${item.zoom} ${item.geomType} ")
                    Button(attrs = {
                        classes("btn", "btn-outline-secondary", "btn-sm")
                        onClick {
                            chartViewModel.setLocation(MapLocation(latitude = item.lat, longitude = item.lng, zoom = item.zoom.toDouble()))
                            routeViewModel.pushRoute(Route.Enc)
                        }
                    }){
                        Text("ENC Zoom")
                    }
                    item.props.filter { !it.value.isEmpty() }.forEach { prop ->
                        Div {
                            B { Text("${prop.key}: ${prop.value}") }
                        }
                    }
                }
            }
        }
        if (pages.lastOrNull()?.lastId != 0L) Button(attrs = {
            classes("btn", "btn-outline-success", "btn-sm")
            onClick {
                viewModel.nextPage()
            }
        }){
            Text("Load more")
        }
    }
}