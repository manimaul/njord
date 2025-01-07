package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.madrona.njord.routing.Route
import io.madrona.njord.viewmodel.*
import org.jetbrains.compose.web.dom.*

@Composable
actual fun ChartInfoPage(id: String) {
    val viewModel = remember { ChartInfoViewModel(id) }
    val state by viewModel.flow.collectAsState()
    state.info.complete(viewModel) { chart ->
        Div(attrs = {
            classes("container", "Content")
        }) {
            Table(attrs = {
                classes("table", "table-striped", "table-bordered", "table-hover")
            }) {
                Thead {
                    Tr {
                        Th { Text("Name") }
                        Th { Text("Value") }
                    }
                }
                Tbody {
                    Tr {
                        Td { Text("ID") }
                        Td { Text("${chart.id}") }
                    }
                    Tr {
                        Td { Text("Name") }
                        Td {
                            Text("${chart.name} ")
                            Button(attrs = {
                                classes("btn", "btn-outline-secondary", "btn-sm")
                                onClick {
                                    chartViewModel.pendingBounds = chart.bounds
                                    routeViewModel.pushRoute(Route.Enc)
                                }
                            }) {
                                Text("ENC Zoom")
                            }
                        }

                    }
                    Tr {
                        Td { Text("Feature Count") }
                        Td { Text("${chart.featureCount}") }
                    }
                    Tr {
                        Td { Text("Updated") }
                        Td { Text(chart.updated) }
                    }
                    Tr {
                        Td { Text("Issued") }
                        Td { Text(chart.issued) }
                    }
                    Tr {
                        Td { Text("Zoom") }
                        Td { Text("${chart.zoom}") }
                    }
                    Tr {
                        Td { Text("Scale") }
                        Td { Text("${chart.scale}") }
                    }
                    Tr {
                        Td { Text("Layers") }
                        Td {
                            P {
                                chart.layers.forEach { layer ->
                                    Link(label = layer, path = "/control/symbols/$layer")
                                    Text(" ")
                                }
                            }
                        }
                    }
                    chart.chartTxt.forEach {
                        Tr {
                            Td { Text(it.key) }
                            Td { Text(it.value) }
                        }
                    }
                    chart.dsidProps.forEach {
                        Tr {
                            Td { Text(it.key) }
                            Td { Text("${it.value}") }
                        }
                    }
                }

            }
        }
    }
}

@Composable
fun ChartInfoList(
    viewModel: ChartInfoViewModel
) {
    val state by viewModel.flow.collectAsState()
    state.info.complete(viewModel) { chart ->

        Ol {
            Li {
                Link(label = "id: ${chart.id}", path = "/chart/${chart.id}")
            }
            Li {
                B { Text("File name: ") }
                Text(chart.fileName)
            }
            Li {
                B { Text("Scale: ") }
                Text(chart.scale.toString())
            }
            Li {
                B { Text("Updated: ") }
                Text(chart.updated)
            }
            Li {
                B { Text("Issued: ") }
                Text(chart.issued)
            }
            Li {
                B { Text("Calculated zoom: ") }
                Text(chart.zoom.toString())
            }
            Li {
                B { Text("Feature count: ") }
                Text(chart.featureCount.toString())
            }
        }
    }
}
