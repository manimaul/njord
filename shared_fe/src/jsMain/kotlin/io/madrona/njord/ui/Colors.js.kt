package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.model.mode
import io.madrona.njord.viewmodel.chartObjectsViewModel
import io.madrona.njord.viewmodel.chartViewModel
import io.madrona.njord.viewmodel.complete
import org.jetbrains.compose.web.css.background
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Text

@Composable
fun Colors() {
    val chartState by chartViewModel.flow.collectAsState()
    val chartObjects by chartObjectsViewModel.flow.collectAsState()
    H2 { Text("Colors") }
        Div(attrs = { classes("row") }) {
            chartObjects.colors.complete(chartObjectsViewModel) { colors ->
                colors[chartState.theme.mode()]?.let { colorMap ->
                    colorMap.forEach {
                        Div(attrs = { classes("col", "mb-3") }) {
                            Text("${it.key.name} ${it.value}")
                            Div(attrs = {
                                style {
                                    height(25.px)
                                    width(25.px)
                                    background(it.value)
                                }
                            })
                        }
                    }
                }
            }
    }

}