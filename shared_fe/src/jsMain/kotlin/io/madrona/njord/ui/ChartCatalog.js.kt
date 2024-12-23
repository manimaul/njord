package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.viewmodel.chartCatalogViewModel
import io.madrona.njord.viewmodel.utils.Complete
import io.madrona.njord.viewmodel.utils.Loading
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.*

@Composable
fun ChartCatalog() {
    val state by chartCatalogViewModel.flow.collectAsState()

    state.catalog.value?.let { catalog ->
        Div {
            H1 { Text("Installed S57 ENCs: ${catalog.totalChartCount}") }
            Input(InputType.Text, attrs = {
                placeholder("Type to filter name...")
                classes("form-control", "my-3")
                onInput {
                    chartCatalogViewModel.filter(it.value)
                }
            })
            Table(
                attrs = {
                    classes("table", "table-striped", "table-bordered", "table-hover")
                }
            ) {
                Thead {
                    Tr {
                        Th { Text("Record ID") }
                        Th { Text("Chart Name") }
                    }
                }
                Tbody {
                    state.filtered.forEachIndexed { i, ea ->
                        Tr {
                            Td {
                                B { Text("${ea.id} ") }
                            }
                            Td {
                                Link(
                                    label = ea.name,
                                    path = "/chart/${ea.id}"
                                )
                                if (state.filter == null)
                                Text(" (${i + 1} of ${catalog.totalChartCount})")
                            }
                        }
                    }
                }
            }
            if (state.catalog is Complete && catalog.page.size < catalog.totalChartCount) {
                Button(attrs = {
                    classes("btn", "btn-primary")
                    onClick {
                        chartCatalogViewModel.loadMore()
                    }
                }) { Text("Load More...") }
            } else if (state.catalog is Loading) {
                LoadingSpinner()
            }
        }

    }

}