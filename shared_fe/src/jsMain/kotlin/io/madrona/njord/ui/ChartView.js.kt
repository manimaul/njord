package io.madrona.njord.ui

import androidx.compose.runtime.*
import io.madrona.njord.geojson.Feature
import io.madrona.njord.js.MapLibre
import io.madrona.njord.js.mapLibreArgs
import io.madrona.njord.viewmodel.chartViewModel
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Text

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
    val features = state.query.mapNotNull { it as? Feature }
    val showHide = chartViewModel.flow.map { it.query.mapNotNull { it as? Feature } }.map { it.isNotEmpty() }
    Modal(
        title = "Chart Query",
        onClose = { chartViewModel.clearQuery() },
        showHideFlow = showHide
    ) {
        DisplayQuery(features)
    }
}

@Composable
fun DisplayQuery(
    content: List<Feature>,
) {
    Div(attrs = {
        id("query-accordion")
        classes("accordion")
    }) {
        content.forEachIndexed { i, feature ->
            Div(attrs = {
                classes("accordion-item")
            }) {
                H2(attrs = { classes("accordion-header") }) {
                    Button(attrs = {
                        classes("accordion-button")
                        attr("data-bs-toggle", "collapse")
                        attr("aria-controls", "collapse$i")
                    }) {
                        Text(feature.geometry?.type.toString())
                    }
                }
                Div(attrs = {
                    classes("accordion-collapse", "collapse")
                }) {

                    Div(attrs = {
//                        id("collapse$i")
                        classes("accordion-collapse", "collapse")
                    }) {

                    }
                    feature.properties.forEach { (key, value) ->

                    }

                }
            }
        }
    }
    /*

  <div class="accordion-item">
    <h2 class="accordion-header">
      <button class="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#collapseOne" aria-expanded="true" aria-controls="collapseOne">
        Accordion Item #1
      </button>
    </h2>
    <div id="collapseOne" class="accordion-collapse collapse show" data-bs-parent="#accordionExample">
      <div class="accordion-body">
        <strong>This is the first item's accordion body.</strong> It is shown by default, until the collapse plugin adds the appropriate classes that we use to style each element. These classes control the overall appearance, as well as the showing and hiding via CSS transitions. You can modify any of this with custom CSS or overriding our default variables. It's also worth noting that just about any HTML can go within the <code>.accordion-body</code>, though the transition does limit overflow.
      </div>
    </div>
  </div>


<div class="accordion" id="accordionExample">
</div>

     */
}
