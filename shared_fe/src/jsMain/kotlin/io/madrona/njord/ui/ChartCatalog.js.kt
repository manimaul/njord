package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.model.ChartCatalog
import io.madrona.njord.viewmodel.ChartCatalogState
import io.madrona.njord.viewmodel.adminViewModel
import io.madrona.njord.viewmodel.chartCatalogViewModel
import io.madrona.njord.viewmodel.utils.Complete
import io.madrona.njord.viewmodel.utils.Loading
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.*

@Composable
fun ChartTable(
    catalog: ChartCatalog,
    state: ChartCatalogState,
) {
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

@Composable
fun ChartCatalog() {
    val state by chartCatalogViewModel.flow.collectAsState()
    val adminState by adminViewModel.flow.collectAsState()

    state.catalog.value?.let { catalog ->
        Div {
            H1 { Text("Installed S57 ENCs: ${catalog.totalChartCount}") }
            state.deleteProgress?.let {
                Progress("Deleting charts $it%", it)
                if (it == 100) {
                    Button(attrs = {
                        classes("btn", "btn-sm", "btn-outline-secondary", "mt-3")
                        onClick { chartCatalogViewModel.reload() }
                    }) {
                        Text("Reload")
                    }
                }
            } ?: run {
                if (catalog.totalChartCount > 0) {
                    Div(attrs = {
                        classes("input-group", "my-3")
                    }) {
                        Input(InputType.Text, attrs = {
                            placeholder("Type to filter name...")
                            classes("form-control")
                            value(state.filter ?: "")
                            onInput {
                                chartCatalogViewModel.filter(it.value)
                            }
                        })
                        Button(attrs = {
                            state.filter?.let {
                                classes("btn", "btn-outline-success")
                            } ?: classes("btn", "btn-outline-success", "disabled")
                            onClick { chartCatalogViewModel.filter("") }
                        }) {
                            Text("Clear")
                        }
                    }
                }
                Button(attrs = {
                    if (adminState.isLoggedIn && state.filtered.isNotEmpty()) {
                        classes("btn", "btn-sm", "btn-danger", "mb-3", "me-3")
                    } else {
                        classes("btn", "btn-sm", "btn-danger", "disabled", "mb-3", "me-3")
                    }
                    onClick { modalViewModel.show() }
                }) {
                    Text("Delete these")
                }
                Button(attrs = {
                    classes("btn", "btn-sm", "btn-outline-secondary", "mb-3")
                    onClick { chartCatalogViewModel.reload() }
                }) {
                    Text("Reload")
                }
                if (catalog.totalChartCount > 0) {
                    ChartTable(catalog, state)
                }
            }
            Modal(
                id = "confirm-delete",
                title = "Are you sure?", { }) {
                H6 { Text("Proceeding will delete ${state.filtered.size} of ${state.catalog.value?.totalChartCount ?: 0} charts?") }
                Button(attrs = {
                    classes("btn", "btn-danger", "btn-sm", "me-2")
                    onClick {
                        modalViewModel.hide()
                        chartCatalogViewModel.deleteAll()
                    }
                }) { Text("Confirm") }
                Button(
                    attrs = {
                        classes("btn", "btn-success", "btn-sm")
                        onClick { modalViewModel.hide() }
                    }
                ) { Text("Cancel") }
            }
        }
    }
}