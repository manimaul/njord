package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import io.madrona.njord.viewmodel.AboutViewModel
import org.jetbrains.compose.web.attributes.colspan
import org.jetbrains.compose.web.dom.*

@Composable
actual fun Home() {
    val viewModel = remember { AboutViewModel() }
    val state = viewModel.flow.collectAsState()
    Div(attrs = { classes("Column", "Fill") }) {
        Div(attrs = { classes("Container", "Fill") }) {
            Header(attrs = { classes("Header") }) {
                Img(src = "/njord.jpg") { classes("img-fluid", "w-25") }
            }
            Div(attrs = { classes("Center") }) {
                Table(attrs = { classes("w-50", "table", "table-striped", "table-bordered", "table-hover") }) {
                    Thead {
                        Tr {
                            Th(attrs = { colspan(2) }) {
                                Text("Njord Electronic Navigation Chart Server")
                            }
                        }
                        Tr {
                            Td { Text("Njord Version") }
                            Td { Text(state.value.response.value?.version ?: "...") }
                        }
                        Tr {
                            Td { Text("Git Commit") }
                            Td {
                                state.value.response.value?.gitHash?.let {
                                    A(href = "https://github.com/manimaul/njord/commit/$it") { Text(it) }
                                }
                            }
                        }
                        Tr {
                            Td { Text("Gdal Version") }
                            Td { Text(state.value.response.value?.gdalVersion ?: "...") }
                        }
                        Tr {
                            Td { Text("Build Date") }
                            Td { Text(state.value.response.value?.buildDate ?: "...") }
                        }
                        //todo: add admin is logged in check
                    }
                }
            }
        }
    }
}
