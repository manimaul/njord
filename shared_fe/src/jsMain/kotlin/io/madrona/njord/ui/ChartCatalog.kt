package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.viewmodel.chartCatalogViewModel
import io.madrona.njord.viewmodel.complete
import org.jetbrains.compose.web.dom.*

@Composable
fun ChartCatalog() {
    val state by chartCatalogViewModel.flow.collectAsState()

    state.catalog.complete(chartCatalogViewModel) { catalog ->
        Div {
            H1 { Text("Installed S57 ENCs: ${catalog.totalChartCount}") }
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
                    catalog.page.forEach { ea ->
                        Tr {
                            Td { Text(ea.id.toString()) }
                            Td {
                                Link(
                                    label = ea.name,
                                    path = "/chart/${ea.id}"
                                )
                            }
                        }
                    }
                }
            }
        }

    }

}