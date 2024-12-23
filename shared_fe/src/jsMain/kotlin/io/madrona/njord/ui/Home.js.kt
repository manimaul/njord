package io.madrona.njord.ui

import androidx.compose.runtime.*
import io.madrona.njord.viewmodel.AboutViewModel
import io.madrona.njord.viewmodel.adminViewModel
import io.madrona.njord.viewmodel.complete
import org.jetbrains.compose.web.attributes.colspan
import org.jetbrains.compose.web.dom.*

@Composable
actual fun Home() {
    val viewModel = remember { AboutViewModel() }
    val state = viewModel.flow.collectAsState()
    val adminState by adminViewModel.flow.collectAsState()
    Div(attrs = { classes("Column", "Fill") }) {
        Div(attrs = { classes("Container", "Fill") }) {
            Header(attrs = { classes("Header") }) {
                Img(src = "/njord.jpg") { classes("img-fluid", "w-25") }
            }
            Div(attrs = { classes("Center") }) {
                state.value.response.complete(viewModel) { info ->
                    Table(attrs = { classes("w-50", "table", "table-striped", "table-bordered", "table-hover") }) {
                        Thead {
                            Tr {
                                Th(attrs = { colspan(2) }) {
                                    Text("Njord Electronic Navigation Chart Server")
                                }
                            }
                            Tr {
                                Td { Text("Njord Version") }
                                Td {
                                    Text(info.version)
                                }
                            }
                            Tr {
                                Td { Text("Git Commit") }
                                Td {
                                    A(href = "https://github.com/manimaul/njord/commit/${info.gitHash}") { Text(info.gitHash) }
                                }
                            }
                            Tr {
                                Td { Text("Gdal Version") }
                                Td {
                                    Text(info.gdalVersion)
                                }
                            }
                            Tr {
                                Td { Text("Build Date") }
                                Td {
                                    Text(info.buildDate)
                                }
                            }

                            adminState.adminSignature.value?.let {
                                Tr {
                                    Td { Text("Admin") }
                                    Td {
                                        Text(it.signature.expirationDate)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
