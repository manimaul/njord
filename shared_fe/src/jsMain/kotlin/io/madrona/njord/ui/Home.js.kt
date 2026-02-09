package io.madrona.njord.ui

import androidx.compose.runtime.*
import io.madrona.njord.viewmodel.AboutViewModel
import io.madrona.njord.viewmodel.adminViewModel
import io.madrona.njord.viewmodel.complete
import kotlinx.datetime.Clock
import kotlinx.datetime.toJSDate
import org.jetbrains.compose.web.attributes.colspan
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.*

@Composable
actual fun Home() {
    val viewModel = remember { AboutViewModel() }
    val state by viewModel.flow.collectAsState()
    val adminState by adminViewModel.flow.collectAsState()
    Div(attrs = { classes("Column", "Fill") }) {
        Div(attrs = { classes("Container", "Fill") }) {
            Header(attrs = { classes("Header") }) {
                Img(src = "/njord.jpg") { classes("img-fluid", "w-25") }
            }
            Div(attrs = { classes("Center") }) {
                state.response.complete(viewModel) { info ->
                    Table(attrs = { classes("w-50", "table", "table-striped", "table-bordered", "table-hover") }) {
                        Thead {
                            Tr {
                                Th(attrs = { colspan(2) }) {
                                    Text(
                                        "Njord Electronic Navigation Chart Server ©${
                                            Clock.System.now().toJSDate().getFullYear()
                                        }"
                                    )
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
                                    A(
                                        attrs = { classes("link-secondary", "link-underline-opacity-75") },
                                        href = "https://github.com/manimaul/njord/commit/${info.gitHash}"
                                    ) {
                                        Text(info.gitHash)
                                    }
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
                            adminState.adminSignatureRemaining?.let {
                                Tr {
                                    Td { Text("Admin") }
                                    Td {
                                        Text(it.toString())
                                    }
                                }
                            }
                            Tr {
                                Td {
                                    Text("License")
                                }
                                Td {
                                    A(
                                        href = "https://github.com/manimaul/njord/blob/master/LICENSE"
                                    ) {
                                        Img(src = "/apache_2_badge.svg")
                                    }
                                }
                            }
                            Tr {
                                Td {
                                    Text("Source")
                                }
                                Td {
                                    A(
                                        href = "https://github.com/manimaul/njord"
                                    ) {
                                        Img(src = "/github.svg", attrs = {
                                            style {
                                                width(25.px)
                                                height(25.px)
                                            }
                                        })
                                    }
                                    Text(" ")
                                    A(
                                        attrs = { classes("link-secondary", "link-underline-opacity-75") },
                                        href = "https://github.com/manimaul/njord"
                                    ) {
                                        Text(" https://github.com/manimaul/njord")
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
